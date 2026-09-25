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
import org.junit.Before
import org.junit.Test

class ObdEngineTest {

    private lateinit var car: FakeCar
    private lateinit var link: FakeLink
    private lateinit var scope: CoroutineScope
    private lateinit var engine: ObdEngine

    @Before
    fun setUp() {
        car = FakeCar()
        link = FakeLink(car::respond)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val elm = Elm327(link.input, link.output)
        elm.start(scope)
        engine = ObdEngine(elm)
    }

    @After
    fun tearDown() {
        link.close()
        scope.cancel()
    }

    @Test
    fun initializeDiscoversVehicle() = runBlocking {
        withTimeout(10_000) { engine.initialize() }
        val state = engine.state.value
        assertEquals(ObdStatus.CONNECTED, state.status)
        assertEquals("ELM327 v2.1", state.adapter)
        assertEquals("ISO 15765-4 (CAN 11/500)", state.protocol)
        assertEquals(car.vin, state.vin)
        assertEquals(listOf("7E8", "7E9"), state.ecus.map { it.header })
        assertEquals("ECM-EngineControl", state.ecus[0].name)
        assertEquals(9, state.ecus[0].supportedPids)
        assertEquals(1, state.ecus[1].supportedPids)
    }

    @Test
    fun pollingReadsValuesFromTheRightEcu() = runBlocking {
        withTimeout(10_000) { engine.initialize() }
        val poll = scope.launch { engine.pollLoop() }
        val state = withTimeout(10_000) {
            engine.state.first { s -> s.values.any { it.key == Keys.BOOST } && s.values.any { it.key == "46" } }
        }
        poll.cancel()

        val values = state.values.associateBy { it.key }
        assertEquals("1726", values.getValue("0C").text)
        assertEquals("60", values.getValue("0D").text)
        assertEquals("83", values.getValue("05").text)
        assertEquals("70", values.getValue("52").text)
        assertEquals("0,85", values.getValue(Keys.BOOST).text)
        assertEquals("25", values.getValue("46").text)
        assertEquals("12,4", values.getValue(Keys.BATTERY).text)
        assertEquals(Keys.BOOST, state.values.first().key)
        assertEquals(false, state.milOn)
        assertEquals(0, state.dtcCount)

        val log = car.log.toList()
        val tcmIndex = log.indexOf("ATSH7E1")
        assertTrue(tcmIndex >= 0)
        assertEquals("0146", log[tcmIndex + 1])
        assertTrue(log.contains("ATSH7E0"))
    }

    @Test
    fun readsTroubleCodes() = runBlocking {
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
}
