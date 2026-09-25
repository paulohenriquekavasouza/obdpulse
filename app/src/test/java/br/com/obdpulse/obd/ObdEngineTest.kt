package br.com.obdpulse.obd

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdEngineTest {

    private val scopes = mutableListOf<CoroutineScope>()
    private val links = mutableListOf<FakeLink>()

    private fun engineFor(car: FakeCar): ObdEngine {
        val link = FakeLink(car::respond).also(links::add)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        val elm = Elm327(link.input, link.output)
        elm.start(scope)
        return ObdEngine(elm)
    }

    @After
    fun tearDown() {
        links.forEach(FakeLink::close)
        scopes.forEach(CoroutineScope::cancel)
    }

    private suspend fun poll(engine: ObdEngine, vararg keys: String): ObdState {
        val job = scopes.first().launch { engine.pollLoop() }
        try {
            return withTimeout(10_000) {
                engine.state.first { s -> keys.all { key -> s.values.any { it.key == key } } }
            }
        } finally {
            job.cancel()
        }
    }

    private fun nextObdCommand(log: List<String>, after: String): String? {
        val index = log.indexOf(after)
        if (index < 0) return null
        return log.drop(index + 1).firstOrNull { !it.startsWith("AT") }
    }

    @Test
    fun initializeDiscoversVehicle() = runBlocking {
        val car = FakeCar()
        val engine = engineFor(car)
        withTimeout(10_000) { engine.initialize() }
        val state = engine.state.value
        assertEquals(ObdStatus.CONNECTED, state.status)
        assertEquals("ELM327 v2.1", state.adapter)
        assertEquals("ISO 15765-4 (CAN 11/500)", state.protocol)
        assertEquals('6', state.protocolNumber)
        assertEquals(car.vin, state.vin)
        assertEquals(listOf("7E8", "7E9"), state.ecus.map { it.header })
        assertEquals("ECM-EngineControl", state.ecus[0].name)
        assertEquals(12, state.ecus[0].supportedPids)
        assertEquals(listOf(0x68), state.ecus[0].undecodedPids)
        assertEquals(1, state.ecus[1].supportedPids)
    }

    @Test
    fun pollingReadsValuesFromTheRightEcu() = runBlocking {
        val car = FakeCar()
        val engine = engineFor(car)
        withTimeout(10_000) { engine.initialize() }
        val initLog = car.log.size
        val state = poll(engine, Keys.BOOST, Keys.CONSUMPTION, "46")

        val values = state.values.associateBy { it.key }
        assertEquals("1726", values.getValue("0C").text)
        assertEquals("60", values.getValue("0D").text)
        assertEquals("83", values.getValue("05").text)
        assertEquals("70", values.getValue("52").text)
        assertEquals("0,85", values.getValue(Keys.BOOST).text)
        assertEquals("25", values.getValue("46").text)
        assertEquals("12,4", values.getValue(Keys.BATTERY).text)
        assertEquals(Keys.CONSUMPTION, state.values.first().key)
        assertEquals(Keys.BOOST, state.values[1].key)
        assertEquals(false, state.milOn)
        assertEquals(0, state.dtcCount)

        val log = car.log.toList().drop(initLog)
        assertEquals("0146", nextObdCommand(log, "ATSH7E1"))
        assertTrue(log.contains("ATSH7E0"))
    }

    @Test
    fun kilometersPerLiterIsComputedAndShownFirst() = runBlocking {
        val engine = engineFor(FakeCar())
        withTimeout(10_000) { engine.initialize() }
        val state = poll(engine, Keys.CONSUMPTION)

        val values = state.values.associateBy { it.key }
        assertEquals("6,0", values.getValue("5E").text)
        assertEquals("10,0", values.getValue(Keys.CONSUMPTION).text)
        assertEquals("km/L", values.getValue(Keys.CONSUMPTION).unit)
        assertEquals(Keys.CONSUMPTION, state.values.first().key)
    }

    @Test
    fun extendedIdsUsePhysicalHeadersAndPreferredProtocol() = runBlocking {
        val car = FakeCar(extended = true, banner = "OKELM327 v1.5")
        val engine = engineFor(car)
        withTimeout(20_000) { engine.initialize(preferredProtocol = '7') }
        val initialized = engine.state.value
        assertEquals("ELM327 v1.5", initialized.adapter)
        assertEquals("ISO 15765-4 (CAN 29/500)", initialized.protocol)
        assertEquals('7', initialized.protocolNumber)
        assertEquals(listOf("18DAF110", "18DAF118"), initialized.ecus.map { it.header })
        assertEquals(car.vin, initialized.vin)
        assertFalse(car.log.contains("ATSP6"))

        val initLog = car.log.size
        val state = poll(engine, "0C", "46")
        assertEquals("1726", state.values.first { it.key == "0C" }.text)
        assertEquals("25", state.values.first { it.key == "46" }.text)
        val log = car.log.toList().drop(initLog)
        assertEquals("0146", nextObdCommand(log, "ATSHDA18F1"))
        assertTrue(log.contains("ATSHDA10F1"))
    }

    @Test
    fun fallsBackToAutomaticSearchForExtendedIds() = runBlocking {
        val car = FakeCar(extended = true)
        val engine = engineFor(car)
        withTimeout(20_000) { engine.initialize() }
        val state = engine.state.value
        assertEquals(ObdStatus.CONNECTED, state.status)
        assertEquals('7', state.protocolNumber)
        assertEquals(listOf("ATSP6", "ATSP7"), car.log.filter { it.startsWith("ATSP") })
    }

    @Test
    fun readsTroubleCodes() = runBlocking {
        val engine = engineFor(FakeCar())
        withTimeout(10_000) { engine.initialize() }
        withTimeout(10_000) { engine.readDtcs() }
        val state = engine.state.value
        val report = state.dtcs!!
        assertEquals(listOf(DtcCode("P0133", "7E8"), DtcCode("P0420", "7E8")), report.stored)
        assertTrue(report.pending.isEmpty())
        assertTrue(report.permanent.isEmpty())
        assertFalse(state.dtcLoading)
        assertEquals(1, state.dtcReadCount)
    }

    @Test
    fun manualFlowControlRecoversMultiFrameResponses() = runBlocking {
        val car = FakeCar(extended = true, brokenAutoFlowControl = true)
        val engine = engineFor(car)
        withTimeout(20_000) { engine.initialize() }
        withTimeout(10_000) { engine.readUndecoded() }
        val state = engine.state.value

        assertEquals(car.vin, state.vin)
        assertEquals(listOf("ECM-EngineControl", "TCM-TransmissionCtrl"), state.ecus.map { it.name })
        assertEquals(listOf(RawPid("18DAF110", 0x68, "01 50 00 00 00 00 00")), state.undecoded)
        val log = car.log.toList()
        assertTrue(log.containsAll(listOf("ATFCSH18DA10F1", "ATFCSD300000", "ATFCSM1", "ATFCSH18DA18F1")))
    }

    @Test
    fun withoutManualFlowControlOnlyTheFirstFrameArrives() = runBlocking {
        val car = FakeCar(extended = true, brokenAutoFlowControl = true)
        val link = FakeLink { command -> if (command.startsWith("ATFC")) "?" else car.respond(command) }.also(links::add)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        val elm = Elm327(link.input, link.output)
        elm.start(scope)
        val engine = ObdEngine(elm)
        withTimeout(20_000) { engine.initialize() }
        val state = engine.state.value

        assertEquals(ObdStatus.CONNECTED, state.status)
        assertEquals(null, state.vin)
        assertEquals("automático", state.rawReplies["flow control"])
        assertEquals("18DAF1101014490201394244", state.rawReplies["0902 DB33F1"])
    }

    @Test
    fun extendedCarDecodesPulseSpecificPids() = runBlocking {
        val engine = engineFor(FakeCar(extended = true))
        withTimeout(20_000) { engine.initialize() }
        val state = poll(engine, Keys.BOOST, "42", "77", "51", "9D", "9E", "03")
        val values = state.values.associateBy { it.key }

        assertEquals("282", values.getValue("0B").text)
        assertEquals("1,87", values.getValue(Keys.BOOST).text)
        assertEquals("14,56", values.getValue("42").text)
        assertEquals(null, values[Keys.BATTERY])
        assertEquals("37", values.getValue("77").text)
        assertEquals("Etanol", values.getValue("51").text)
        assertEquals("Malha fechada (sonda lambda)", values.getValue("03").text)
        assertEquals("0,18", values.getValue("9D").text)
        assertEquals("8,4", values.getValue("9E").text)
        assertEquals("máximo 400 kPa", state.rawReplies["escala do MAP (PID 4F)"])
    }

    @Test
    fun diagnosticsListUndecodedPidsWithRawBytes() = runBlocking {
        val engine = engineFor(FakeCar(extended = true))
        withTimeout(20_000) { engine.initialize() }
        withTimeout(10_000) { engine.readUndecoded() }
        val state = engine.state.value
        assertEquals(listOf(RawPid("18DAF110", 0x68, "01 50 00 00 00 00 00")), state.undecoded)

        val report = Diagnostics.report(state)
        assertTrue(report.contains("Central 18DAF110 (ECM-EngineControl): 18 parâmetros"))
        assertTrue(report.contains("Sem decodificação: 68"))
        assertTrue(report.contains("18DAF110 PID 68: 01 50 00 00 00 00 00"))
        assertTrue(report.contains("escala do MAP (PID 4F): máximo 400 kPa"))
        assertTrue(report.contains("flow control: manual (18DA10F1)"))
        assertTrue(report.contains("VIN: ${FakeCar().vin}"))
    }
}
