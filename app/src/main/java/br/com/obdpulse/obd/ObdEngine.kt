package br.com.obdpulse.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

class ObdEngine(private val elm: Elm327) {

    private val _state = MutableStateFlow(ObdState(status = ObdStatus.INITIALIZING))
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private val lock = Mutex()
    private var currentHeader: String? = null
    private var extendedIds = false
    private val owners = LinkedHashMap<Int, String>()
    private val plan = mutableListOf<PidDef>()
    private val misses = HashMap<Int, Int>()
    private val values = LinkedHashMap<String, LiveValue>()
    private var failedCycles = 0

    suspend fun initialize(preferredProtocol: Char? = null) {
        _state.update { it.copy(status = ObdStatus.INITIALIZING, message = null) }
        val adapter = ADAPTER_VERSION.find(elm.send("ATZ", RESET_TIMEOUT))?.value?.trim()
        for (command in SETUP) elm.send(command)

        var first = emptyList<EcuMessage>()
        for (protocol in probeOrder(preferredProtocol)) {
            first = probe("ATSP$protocol", Elm327.DEFAULT_TIMEOUT)
            if (first.isNotEmpty()) break
        }
        if (first.isEmpty()) first = probe("ATSP0", SEARCH_TIMEOUT)
        if (first.isEmpty()) {
            throw ObdException("O veículo não respondeu. Ligue a ignição e tente novamente.")
        }
        extendedIds = first.any { it.header.length == 8 }
        currentHeader = functionalHeader()
        val protocol = ElmParser.textLines(elm.send("ATDP")).lastOrNull()
        val protocolNumber = parseProtocolNumber(ElmParser.textLines(elm.send("ATDPN")).lastOrNull())

        val supported = discoverSupported(first)
        owners.clear()
        supported.forEach { (header, pids) -> pids.forEach { owners.putIfAbsent(it, header) } }
        plan.clear()
        plan += Pids.all.filter { it.pid in owners }

        val raw = LinkedHashMap<String, String>()
        val vin = readVin(raw)
        val names = readEcuNames(raw)
        _state.update {
            it.copy(
                status = ObdStatus.CONNECTED,
                adapter = adapter,
                protocol = protocol,
                protocolNumber = protocolNumber,
                vin = vin,
                ecus = supported.map { (header, pids) -> EcuInfo(header, names[header], pids.toList()) },
                rawReplies = raw,
            )
        }
    }

    suspend fun pollLoop() {
        var cycle = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val full = cycle % SLOW_EVERY == 0L
            var attempted = 0
            var succeeded = 0
            val missed = mutableListOf<PidDef>()
            for (def in plan.toList()) {
                if (!def.fast && !full) continue
                attempted++
                if (readPid(def)) succeeded++ else missed += def
            }
            if (full) {
                readMil()
                readBattery()
            }
            if (attempted > 0) {
                if (succeeded == 0) {
                    failedCycles++
                    if (failedCycles >= MAX_FAILED_CYCLES) {
                        throw ObdException("O veículo parou de responder.")
                    }
                } else {
                    failedCycles = 0
                    for (def in missed) {
                        val count = (misses[def.pid] ?: 0) + 1
                        misses[def.pid] = count
                        if (count >= MAX_MISSES) {
                            plan.remove(def)
                            values.remove(def.key)
                        }
                    }
                }
            }
            updateDerived()
            _state.update { it.copy(values = values.values.sortedBy(LiveValue::priority)) }
            cycle++
            if (plan.isEmpty()) delay(IDLE_DELAY) else yield()
        }
    }

    suspend fun readDtcs() {
        _state.update { it.copy(dtcLoading = true) }
        try {
            val header = functionalHeader()
            val stored = Dtc.parse(request(header, "03", DTC_TIMEOUT), 0x43)
            val pending = Dtc.parse(request(header, "07", DTC_TIMEOUT), 0x47)
            val permanent = Dtc.parse(request(header, "0A", DTC_TIMEOUT), 0x4A)
            _state.update { it.copy(dtcs = DtcReport(stored, pending, permanent), message = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(message = "Não foi possível ler as falhas: ${e.message}") }
        } finally {
            _state.update { it.copy(dtcLoading = false, dtcReadCount = it.dtcReadCount + 1) }
        }
    }

    suspend fun readUndecoded() {
        val result = mutableListOf<RawPid>()
        for (ecu in _state.value.ecus) {
            for (pid in ecu.undecodedPids) {
                val reply = try {
                    request(physicalHeader(ecu.header), "01%02X".format(pid))
                } catch (e: ElmTimeoutException) {
                    result += RawPid(ecu.header, pid, NO_REPLY)
                    continue
                }
                val message = reply.firstOrNull {
                    it.header == ecu.header && it.data.size >= 2 && it.data[0] == 0x41 && it.data[1] == pid
                }
                result += RawPid(ecu.header, pid, message?.let { hex(it.data.drop(2)) } ?: NO_REPLY)
            }
        }
        _state.update { it.copy(undecoded = result) }
    }

    private fun probeOrder(preferred: Char?): List<Char> =
        (listOfNotNull(preferred) + DEFAULT_PROBES).filter { it != '0' }.distinct()

    private suspend fun probe(protocolCommand: String, timeoutMs: Long): List<EcuMessage> {
        elm.send(protocolCommand)
        return try {
            ElmParser.messages(elm.send("0100", timeoutMs)).filter { isSupportPage(it, 0x00) }
        } catch (e: ElmTimeoutException) {
            emptyList()
        }
    }

    private suspend fun discoverSupported(first: List<EcuMessage>): Map<String, Set<Int>> {
        val supported = sortedMapOf<String, MutableSet<Int>>()
        var page = first
        var base = 0x00
        while (true) {
            var more = false
            for (message in page) {
                if (!isSupportPage(message, base)) continue
                val pids = supported.getOrPut(message.header) { sortedSetOf() }
                for (i in 0 until 32) {
                    val byte = message.data[2 + i / 8]
                    if ((byte shr (7 - i % 8)) and 1 == 1) pids += base + i + 1
                }
                if (base + 0x20 in pids) more = true
            }
            if (!more || base >= 0xE0) break
            base += 0x20
            page = try {
                request(functionalHeader(), "01%02X".format(base))
            } catch (e: ElmTimeoutException) {
                break
            }
        }
        return supported
    }

    private fun isSupportPage(message: EcuMessage, base: Int) =
        message.data.size >= 6 && message.data[0] == 0x41 && message.data[1] == base

    private suspend fun readVin(raw: MutableMap<String, String>): String? {
        val targets = listOf(functionalHeader()) + owners[0x01]?.let { listOf(physicalHeader(it)) }.orEmpty()
        for (header in targets.distinct()) {
            val text = try {
                requestRaw(header, "0902", INFO_TIMEOUT)
            } catch (e: ElmTimeoutException) {
                raw["0902 $header"] = NO_REPLY
                continue
            }
            raw["0902 $header"] = compact(text)
            val vin = ElmParser.messages(text)
                .firstOrNull { it.data.size > 3 && it.data[0] == 0x49 && it.data[1] == 0x02 }
                ?.let { ElmParser.ascii(it.data, 3) }
                ?.takeIf { it.isNotEmpty() }
            if (vin != null) return vin
        }
        return null
    }

    private suspend fun readEcuNames(raw: MutableMap<String, String>): Map<String, String> {
        val text = try {
            requestRaw(functionalHeader(), "090A", INFO_TIMEOUT)
        } catch (e: ElmTimeoutException) {
            raw["090A"] = NO_REPLY
            return emptyMap()
        }
        raw["090A"] = compact(text)
        return ElmParser.messages(text)
            .filter { it.data.size > 3 && it.data[0] == 0x49 && it.data[1] == 0x0A }
            .associate { it.header to ElmParser.ascii(it.data, 3) }
            .filterValues { it.isNotEmpty() }
    }

    private suspend fun readPid(def: PidDef): Boolean {
        val owner = owners[def.pid] ?: return false
        val reply = try {
            request(physicalHeader(owner), "01%02X".format(def.pid))
        } catch (e: ElmTimeoutException) {
            return false
        }
        val message = reply.firstOrNull {
            it.header == owner && it.data.size >= 2 + def.length && it.data[0] == 0x41 && it.data[1] == def.pid
        } ?: return false
        val value = def.decode(message.data.copyOfRange(2, 2 + def.length))
        values[def.key] = LiveValue(def.key, def.name, value, Format.number(value, def.decimals), def.unit, def.priority)
        misses.remove(def.pid)
        return true
    }

    private suspend fun readMil() {
        val owner = owners[0x01] ?: return
        val reply = try {
            request(physicalHeader(owner), "0101")
        } catch (e: ElmTimeoutException) {
            return
        }
        val message = reply.firstOrNull {
            it.header == owner && it.data.size >= 3 && it.data[0] == 0x41 && it.data[1] == 0x01
        } ?: return
        val a = message.data[2]
        _state.update { it.copy(milOn = a and 0x80 != 0, dtcCount = a and 0x7F) }
    }

    private suspend fun readBattery() {
        val text = try {
            elm.send("ATRV")
        } catch (e: ElmTimeoutException) {
            return
        }
        val volts = VOLTAGE.find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return
        values[Keys.BATTERY] = LiveValue(
            Keys.BATTERY, Labels.name(Keys.BATTERY), volts, Format.number(volts, 1), "V", Pids.priority(Keys.BATTERY),
        )
    }

    private fun updateDerived() {
        val map = values["0B"]?.value
        val baro = values["33"]?.value
        if (map != null && baro != null) {
            val bar = (map - baro) / 100.0
            values[Keys.BOOST] = LiveValue(
                Keys.BOOST, Labels.name(Keys.BOOST), bar, Format.number(bar, 2), "bar", Pids.priority(Keys.BOOST),
            )
        }
        val rate = values["5E"]?.value
        val speed = values["0D"]?.value
        if (rate != null && speed != null) {
            val kmPerLiter = if (rate < MIN_FUEL_RATE) Double.NaN else speed / rate
            val text = when {
                kmPerLiter.isNaN() -> EMPTY
                kmPerLiter > MAX_KM_PER_LITER -> "> " + Format.number(MAX_KM_PER_LITER, 1)
                else -> Format.number(kmPerLiter, 1)
            }
            values[Keys.CONSUMPTION] = LiveValue(
                Keys.CONSUMPTION, Labels.name(Keys.CONSUMPTION), kmPerLiter, text, "km/L", Pids.priority(Keys.CONSUMPTION),
            )
        }
    }

    private suspend fun request(
        header: String?,
        command: String,
        timeoutMs: Long = Elm327.DEFAULT_TIMEOUT,
    ): List<EcuMessage> = ElmParser.messages(requestRaw(header, command, timeoutMs))

    private suspend fun requestRaw(header: String?, command: String, timeoutMs: Long): String = lock.withLock {
        if (header != null && header != currentHeader) {
            elm.send("ATSH$header")
            currentHeader = header
        }
        elm.send(command, timeoutMs)
    }

    private fun functionalHeader(): String = if (extendedIds) FUNCTIONAL_HEADER_29 else FUNCTIONAL_HEADER_11

    private fun physicalHeader(responseHeader: String): String =
        if (responseHeader.length == 8) {
            "DA" + responseHeader.substring(6, 8) + responseHeader.substring(4, 6)
        } else {
            "%03X".format(responseHeader.toInt(16) - 8)
        }

    private fun parseProtocolNumber(text: String?): Char? {
        val clean = text?.trim()?.uppercase().orEmpty()
        return when {
            clean.length == 2 && clean[0] == 'A' -> clean[1]
            clean.length == 1 -> clean[0]
            else -> null
        }
    }

    private fun compact(text: String) = ElmParser.textLines(text).joinToString(" | ")

    private fun hex(bytes: List<Int>) = bytes.joinToString(" ") { "%02X".format(it) }

    companion object {
        private const val FUNCTIONAL_HEADER_11 = "7DF"
        private const val FUNCTIONAL_HEADER_29 = "DB33F1"
        private const val RESET_TIMEOUT = 6_000L
        private const val SEARCH_TIMEOUT = 15_000L
        private const val INFO_TIMEOUT = 5_000L
        private const val DTC_TIMEOUT = 4_000L
        private const val SLOW_EVERY = 10L
        private const val MAX_MISSES = 3
        private const val MAX_FAILED_CYCLES = 5
        private const val IDLE_DELAY = 1_000L
        private const val MIN_FUEL_RATE = 0.1
        private const val MAX_KM_PER_LITER = 99.9
        private const val EMPTY = "—"
        const val NO_REPLY = "sem resposta"
        private val DEFAULT_PROBES = listOf('6', '7')
        private val SETUP = listOf("ATE0", "ATL0", "ATS0", "ATH1", "ATAT1")
        private val VOLTAGE = Regex("(\\d{1,2}[.,]\\d)")
        private val ADAPTER_VERSION = Regex("ELM327[^\\r\\n>]*", RegexOption.IGNORE_CASE)
    }
}
