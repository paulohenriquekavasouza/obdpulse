package br.com.obdpulse.media

import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.obd.TripStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GaugeDataTest {

    private val connected = ObdState(
        status = ObdStatus.CONNECTED,
        values = listOf(
            LiveValue(Keys.CONSUMPTION, "km/L", 12.3, "12,3", "km/L", 0),
            LiveValue("0C", "Rotação", 1726.0, "1726", "rpm", 10),
            LiveValue("0D", "Velocidade", 60.0, "60", "km/h", 20),
            LiveValue("05", "Motor", 92.0, "92", "°C", 30),
        ),
        trip = TripStats(lastSub10Ms = 8730),
    )

    @Test
    fun extractsSpeedAndGridValues() {
        val data = GaugeData.from(connected)
        assertTrue(data.connected)
        assertEquals(60.0, data.speed!!, 0.0001)
        assertEquals("60", data.speedText)
        assertEquals("12,3", data.kmpl)
        assertEquals("1726 rpm", data.rpm)
        assertEquals("92 °C", data.coolant)
        assertEquals("8,7 s", data.lastZeroTo100)
    }

    @Test
    fun disconnectedShowsDashesAndNoSpeed() {
        val data = GaugeData.from(ObdState())
        assertFalse(data.connected)
        assertNull(data.speed)
        assertEquals(GaugeData.EMPTY, data.speedText)
        assertEquals(GaugeData.EMPTY, data.kmpl)
        assertEquals(GaugeData.EMPTY, data.lastZeroTo100)
    }

    @Test
    fun signatureChangesWithValuesButNotWithUnrelatedState() {
        val base = GaugeData.from(connected).signature()
        val sameValues = GaugeData.from(connected.copy(dtcReadCount = 5)).signature()
        val changed = GaugeData.from(
            connected.copy(values = connected.values.map { if (it.key == "0D") it.copy(text = "80") else it }),
        ).signature()
        assertEquals(base, sameValues)
        assertTrue(base != changed)
    }
}
