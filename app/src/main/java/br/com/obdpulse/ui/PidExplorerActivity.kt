package br.com.obdpulse.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import br.com.obdpulse.ObdManager
import br.com.obdpulse.R
import br.com.obdpulse.net.Ntfy
import br.com.obdpulse.obd.EcuInfo
import br.com.obdpulse.obd.EcuPing
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.PidDecoder
import br.com.obdpulse.obd.ProbeResult
import br.com.obdpulse.obd.ProbeStatus
import br.com.obdpulse.obd.ScanDiag
import br.com.obdpulse.obd.ScanReport
import br.com.obdpulse.obd.ScanRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PidExplorerActivity : Activity() {

    private val scope = MainScope()
    private var monitorJob: Job? = null
    private var scanJob: Job? = null
    private var stateJob: Job? = null

    private lateinit var scan: Button
    private lateinit var scanEcus: Button
    private lateinit var stop: Button
    private lateinit var copyScan: Button
    private lateinit var session: Switch
    private lateinit var scanProgress: TextView

    private lateinit var ecu: Spinner
    private lateinit var service: Spinner
    private lateinit var pidValue: EditText
    private lateinit var read: Button
    private lateinit var monitor: Switch
    private lateinit var copy: Button
    private lateinit var result: TextView
    private lateinit var candidates: TextView
    private lateinit var refreshUndecoded: Button
    private lateinit var undecoded: TextView

    private var ecus: List<EcuInfo> = emptyList()
    private var lastResult: ProbeResult? = null
    private val scanRows = mutableListOf<ScanRow>()
    private val scanDiags = mutableListOf<ScanDiag>()
    private var lastReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pid_explorer)
        scan = findViewById(R.id.scan)
        scanEcus = findViewById(R.id.scan_ecus)
        stop = findViewById(R.id.stop)
        copyScan = findViewById(R.id.copy_scan)
        session = findViewById(R.id.session)
        scanProgress = findViewById(R.id.scan_progress)
        ecu = findViewById(R.id.ecu)
        service = findViewById(R.id.service)
        pidValue = findViewById(R.id.pid_value)
        read = findViewById(R.id.read)
        monitor = findViewById(R.id.monitor)
        copy = findViewById(R.id.copy)
        result = findViewById(R.id.result)
        candidates = findViewById(R.id.candidates)
        refreshUndecoded = findViewById(R.id.refresh_undecoded)
        undecoded = findViewById(R.id.undecoded)

        service.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.pid_service_01), getString(R.string.pid_service_22)),
        )
        buildEcuSpinner(ObdManager.state.value)

        scan.setOnClickListener { startScan() }
        scanEcus.setOnClickListener { startEcuScan() }
        stop.setOnClickListener { scanJob?.cancel() }
        copyScan.setOnClickListener { copyScanReport() }
        read.setOnClickListener { probeOnce() }
        copy.setOnClickListener { copyResult() }
        refreshUndecoded.setOnClickListener { loadUndecoded() }
        monitor.setOnCheckedChangeListener { _, checked -> if (checked) startMonitor() else monitorJob?.cancel() }
    }

    override fun onStart() {
        super.onStart()
        stateJob = scope.launch { ObdManager.state.collect { buildEcuSpinner(it) } }
    }

    override fun onStop() {
        monitor.isChecked = false
        monitorJob?.cancel()
        scanJob?.cancel()
        stateJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildEcuSpinner(state: ObdState) {
        if (state.ecus.map { it.header } == ecus.map { it.header }) return
        val selected = ecu.selectedItemPosition
        ecus = state.ecus
        ecu.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ecus.map { "${it.header}  (${it.label ?: "—"})" },
        )
        if (selected in ecus.indices) ecu.setSelection(selected)
    }

    private fun startScan() {
        if (ecus.isEmpty()) {
            Toast.makeText(this, R.string.pid_need_connection, Toast.LENGTH_SHORT).show()
            return
        }
        monitor.isChecked = false
        scanRows.clear()
        scanDiags.clear()
        scan.isEnabled = false
        scanEcus.isEnabled = false
        stop.isEnabled = true
        copyScan.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scanJob = scope.launch {
            try {
                ObdManager.setFastScanTiming(true)
                runScan()
                publish(getString(R.string.pid_scan_done, scanRows.size), buildReport())
            } catch (e: kotlinx.coroutines.CancellationException) {
                scanProgress.text = getString(R.string.pid_scan_stopped, scanRows.size)
                throw e
            } finally {
                finishScanUi()
            }
        }
    }

    private fun startEcuScan() {
        if (ecus.isEmpty()) {
            Toast.makeText(this, R.string.pid_need_connection, Toast.LENGTH_SHORT).show()
            return
        }
        monitor.isChecked = false
        scan.isEnabled = false
        scanEcus.isEnabled = false
        stop.isEnabled = true
        copyScan.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scanJob = scope.launch {
            try {
                ObdManager.setFastScanTiming(true)
                val pings = runEcuScan()
                val report = ScanReport.formatEcus(pings, ObdManager.state.value.vin)
                publish(getString(R.string.pid_ecu_scan_done, pings.count { it.present }), report)
            } catch (e: kotlinx.coroutines.CancellationException) {
                scanProgress.text = getString(R.string.pid_scan_stopped, 0)
                throw e
            } finally {
                finishScanUi()
            }
        }
    }

    private suspend fun runEcuScan(): List<EcuPing> {
        val found = mutableListOf<EcuPing>()
        val addresses = if (ObdManager.extendedAddressing()) (0x00..0xFF).toList() else (0x7E0..0x7EF).toList()
        for ((index, tx) in addresses.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (index % 4 == 0) scanProgress.text = getString(R.string.pid_ecu_scan_running, tx, found.size)
            val ping = ObdManager.pingEcu(tx)
            if (ping != null && ping.present) {
                found += ping
                scanProgress.text = getString(R.string.pid_ecu_scan_running, tx, found.size)
            }
        }
        return found
    }

    private fun finishScanUi() {
        scope.launch { ObdManager.setFastScanTiming(false) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scan.isEnabled = true
        scanEcus.isEnabled = true
        stop.isEnabled = false
        copyScan.isEnabled = true
    }

    private fun buildReport(): String =
        ScanReport.format(scanRows.toList(), scanDiags.toList(), ObdManager.state.value.vin)

    private suspend fun publish(doneText: String, report: String) {
        lastReport = report
        val ok = Ntfy.publish(NTFY_TOPIC, "OBD Pulse", report)
        scanProgress.text = "$doneText " + getString(if (ok) R.string.pid_scan_sent else R.string.pid_scan_send_failed)
    }

    private suspend fun runScan() {
        ObdManager.refreshUndecoded()
        for (item in ObdManager.state.value.undecoded) {
            val bytes = item.hex.split(' ').mapNotNull { it.toIntOrNull(16) }
            if (bytes.isNotEmpty()) {
                scanRows += ScanRow(item.ecu, labelOf(item.ecu), PidDecoder.SERVICE_LIVE, item.pid, bytes)
            }
        }
        val extended = session.isChecked
        val curated = listOf(0xF190, 0xF18C, 0xF187, 0xF191, 0xF195)
        val dids = (curated + DID_RANGES.flatMap { it }).distinct()
        for (target in ecus) {
            var tried = 0
            var ok = 0
            var negative = 0
            var noData = 0
            var noReply = 0
            var consecutiveNoReply = 0
            val samples = mutableListOf<String>()
            var sessionNote: String? = null
            var sessionOpen = false
            if (extended) {
                val notes = mutableListOf<String>()
                for (sub in SESSION_SUBS) {
                    currentCoroutineContext().ensureActive()
                    val opened = ObdManager.startSession(target.header, sub)
                    notes += "10%02X→%s".format(sub, describeSession(opened))
                    if (opened?.status == ProbeStatus.OK) {
                        sessionOpen = true
                        break
                    }
                }
                sessionNote = notes.joinToString(", ")
            }
            for (did in dids) {
                currentCoroutineContext().ensureActive()
                tried++
                if (sessionOpen && tried % TESTER_EVERY == 0) ObdManager.testerPresent(target.header)
                if (tried % PROGRESS_STEP == 0) {
                    scanProgress.text = getString(R.string.pid_scan_running, target.header, did, scanRows.size)
                }
                val probe = ObdManager.probe(target.header, PidDecoder.SERVICE_EXTENDED, did, SWEEP_TIMEOUT)
                when (probe?.status) {
                    ProbeStatus.OK -> {
                        scanRows += ScanRow(target.header, target.label, PidDecoder.SERVICE_EXTENDED, did, probe.payload)
                        ok++
                        consecutiveNoReply = 0
                        scanProgress.text = getString(R.string.pid_scan_running, target.header, did, scanRows.size)
                    }
                    ProbeStatus.NEGATIVE -> {
                        negative++
                        consecutiveNoReply = 0
                    }
                    ProbeStatus.MISMATCH -> {
                        noData++
                        consecutiveNoReply = 0
                    }
                    ProbeStatus.NO_REPLY ->
                        if (probe.adapterText.isEmpty()) {
                            noReply++
                            consecutiveNoReply++
                        } else {
                            noData++
                            consecutiveNoReply = 0
                        }
                    null -> {
                        noReply++
                        consecutiveNoReply++
                    }
                }
                if (samples.size < SAMPLE_LIMIT) {
                    val text = probe?.adapterText?.ifEmpty { "(sem resposta)" } ?: "(desconectado)"
                    samples += "%04X: %s".format(did, text)
                }
                if (consecutiveNoReply >= DEAD_LIMIT) break
            }
            scanDiags += ScanDiag(target.header, target.label, tried, ok, negative, noData, noReply, samples, sessionNote)
        }
    }

    private fun describeSession(probe: ProbeResult?): String = when (probe?.status) {
        ProbeStatus.OK -> "ok"
        ProbeStatus.NEGATIVE -> "NRC 0x%02X".format(probe.nrc ?: 0)
        ProbeStatus.MISMATCH -> "inesperado"
        ProbeStatus.NO_REPLY -> if (probe.adapterText.isEmpty()) "s/resposta" else probe.adapterText.take(14)
        null -> "desconectado"
    }

    private fun labelOf(header: String): String? = ecus.firstOrNull { it.header == header }?.label

    private fun copyScanReport() {
        val text = lastReport.ifEmpty { buildReport() }
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.pid_title), text))
        Toast.makeText(this, R.string.pid_copied, Toast.LENGTH_SHORT).show()
    }

    private fun selectedService(): Int =
        if (service.selectedItemPosition == 1) PidDecoder.SERVICE_EXTENDED else PidDecoder.SERVICE_LIVE

    private fun probeOnce() {
        val target = ecus.getOrNull(ecu.selectedItemPosition)
        if (target == null) {
            Toast.makeText(this, R.string.pid_need_connection, Toast.LENGTH_SHORT).show()
            return
        }
        val pid = PidDecoder.parsePid(pidValue.text.toString())
        if (pid == null) {
            Toast.makeText(this, R.string.pid_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch { renderResult(ObdManager.probe(target.header, selectedService(), pid)) }
    }

    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                val target = ecus.getOrNull(ecu.selectedItemPosition)
                val pid = PidDecoder.parsePid(pidValue.text.toString())
                if (target == null || pid == null) {
                    monitor.isChecked = false
                    break
                }
                renderResult(ObdManager.probe(target.header, selectedService(), pid))
                delay(MONITOR_INTERVAL)
            }
        }
    }

    private fun renderResult(probe: ProbeResult?) {
        if (probe == null) {
            result.text = getString(R.string.pid_need_connection)
            candidates.visibility = View.GONE
            lastResult = null
            return
        }
        lastResult = probe
        val head = "${probe.command}  @ ${probe.header}"
        when (probe.status) {
            ProbeStatus.OK -> {
                result.text = "$head\n${PidDecoder.hex(probe.payload)}"
                val list = PidDecoder.candidates(probe.payload)
                candidates.text = list.joinToString("\n") { "${it.label}:  ${it.value}" }
                candidates.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
            ProbeStatus.NO_REPLY -> {
                result.text = "$head\n${getString(R.string.pid_status_no_reply)}"
                candidates.visibility = View.GONE
            }
            ProbeStatus.NEGATIVE -> {
                result.text = "$head\n${getString(R.string.pid_status_negative, probe.nrc ?: 0)}"
                candidates.visibility = View.GONE
            }
            ProbeStatus.MISMATCH -> {
                result.text = "$head\n${getString(R.string.pid_status_mismatch)}\n${probe.rawHex}"
                candidates.visibility = View.GONE
            }
        }
    }

    private fun copyResult() {
        val probe = lastResult ?: return
        val text = buildString {
            append(probe.command).append(" @ ").append(probe.header).append('\n')
            append(probe.rawHex).append('\n')
            PidDecoder.candidates(probe.payload).forEach { append(it.label).append(": ").append(it.value).append('\n') }
        }
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.pid_title), text))
        Toast.makeText(this, R.string.pid_copied, Toast.LENGTH_SHORT).show()
    }

    private fun loadUndecoded() {
        refreshUndecoded.isEnabled = false
        scope.launch {
            ObdManager.refreshUndecoded()
            val raw = ObdManager.state.value.undecoded
            undecoded.text = if (raw.isEmpty()) {
                "—"
            } else {
                raw.joinToString("\n") { item ->
                    val bytes = item.hex.split(' ').mapNotNull { it.toIntOrNull(16) }
                    val best = PidDecoder.candidates(bytes).take(2).joinToString("  ·  ") { "${it.label}=${it.value}" }
                    val tail = if (best.isEmpty()) item.hex else "${item.hex}   →   $best"
                    "%s · %02X: %s".format(item.ecu, item.pid, tail)
                }
            }
            undecoded.visibility = View.VISIBLE
            refreshUndecoded.isEnabled = true
        }
    }

    private companion object {
        const val NTFY_TOPIC = "logsobdpulse"
        const val MONITOR_INTERVAL = 350L
        const val SWEEP_TIMEOUT = 700L
        const val PROGRESS_STEP = 8
        const val DEAD_LIMIT = 16
        const val SAMPLE_LIMIT = 14
        const val TESTER_EVERY = 25
        val SESSION_SUBS = listOf(0x03, 0x02, 0x01, 0x04, 0x40, 0x60, 0x7E)
        val DID_RANGES = listOf(
            0xF180..0xF1FF,
            0x0100..0x03FF,
            0x1900..0x1AFF,
            0x1C00..0x1CFF,
        )
    }
}
