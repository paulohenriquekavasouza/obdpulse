package br.com.obdpulse.media

import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GaugeRendererTest {

    @Test
    fun rendersSquareBitmapWhenConnected() {
        val state = ObdState(
            status = ObdStatus.CONNECTED,
            values = listOf(
                LiveValue(Keys.BOOST, "Turbo", 1.6, "1,60", "bar", 0),
                LiveValue(Keys.CONSUMPTION, "km/L", 9.0, "9,0", "km/L", 0),
                LiveValue("0C", "Rotação", 4200.0, "4200", "rpm", 10),
                LiveValue("0D", "Velocidade", 120.0, "120", "km/h", 20),
                LiveValue("05", "Motor", 95.0, "95", "°C", 30),
            ),
        )
        val bitmap = GaugeRenderer().render(GaugeData.from(state))
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun rendersWhenDisconnected() {
        val bitmap = GaugeRenderer().render(GaugeData.from(ObdState()))
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }
}
