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
            LiveValue(Keys.BOOST, "Turbo", 0.85, "0,85", "bar", 0),
            LiveValue(Keys.CONSUMPTION, "km/L", 12.3, "12,3", "km/L", 0),
            LiveValue("0C", "Rotação", 1726.0, "1726", "rpm", 10),
            LiveValue("0D", "Velocidade", 60.0, "60", "km/h", 20),
            LiveValue("05", "Motor", 92.0, "92", "°C", 30),
        ),
        trip = TripStats(maxBoost = 1.32),
    )

    @Test
    fun extractsGaugeValuesFromState() {
        val data = GaugeData.from(connected)
        assertTrue(data.connected)
        assertEquals(0.85, data.boost!!, 0.0001)
        assertEquals("0,85", data.boostText)
        assertEquals(1.32, data.boostMax!!, 0.0001)
        assertEquals("12,3", data.kmpl)
        assertEquals("1726 rpm", data.rpm)
        assertEquals("60 km/h", data.speed)
        assertEquals("92 °C", data.coolant)
    }

    @Test
    fun disconnectedShowsDashesAndNoBoost() {
        val data = GaugeData.from(ObdState())
        assertFalse(data.connected)
        assertNull(data.boost)
        assertEquals(GaugeData.EMPTY, data.boostText)
        assertEquals(GaugeData.EMPTY, data.kmpl)
    }

    @Test
    fun signatureChangesWithValuesButNotElse() {
        val base = GaugeData.from(connected).signature()
        val sameValues = GaugeData.from(connected.copy(dtcReadCount = 5)).signature()
        val changed = GaugeData.from(
            connected.copy(values = connected.values.map { if (it.key == "0D") it.copy(text = "80") else it }),
        ).signature()
        assertEquals(base, sameValues)
        assertTrue(base != changed)
    }
}
