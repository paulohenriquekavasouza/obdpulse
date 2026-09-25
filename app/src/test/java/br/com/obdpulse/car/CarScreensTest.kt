package br.com.obdpulse.car

import androidx.car.app.HandshakeInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.car.app.versioning.CarAppApiLevels
import androidx.test.core.app.ApplicationProvider
import br.com.obdpulse.R
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.Labels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarScreensTest {

    private fun carContext(apiLevel: Int): TestCarContext =
        TestCarContext.createCarContext(ApplicationProvider.getApplicationContext()).apply {
            updateHandshakeInfo(HandshakeInfo("com.google.android.projection.gearhead", apiLevel))
        }

    private fun rows(template: ListTemplate): List<Row> = template.singleList!!.items.map { it as Row }

    @Test
    fun liveScreenUsesHeaderOnModernHosts() {
        val context = carContext(CarAppApiLevels.LEVEL_8)
        val template = LiveDataScreen(context).onGetTemplate() as ListTemplate

        val header = template.header!!
        assertEquals(context.getString(R.string.app_name), header.title.toString())
        assertEquals(Action.TYPE_APP_ICON, header.startHeaderAction!!.type)
        assertEquals(1, header.endHeaderActions.size)
        assertNotNull(header.endHeaderActions[0].icon)

        val rows = rows(template)
        assertEquals(context.getString(R.string.car_status), rows[0].title.toString())
        assertEquals(context.getString(R.string.car_disconnected), rows[0].texts[0].toString())
        assertEquals(Labels.name(Keys.BOOST), rows[1].title.toString())
        assertTrue(rows.drop(1).all { it.texts[0].toString() == "—" })
    }

    @Suppress("DEPRECATION")
    @Test
    fun liveScreenFallsBackOnLegacyHosts() {
        val context = carContext(CarAppApiLevels.LEVEL_1)
        val template = LiveDataScreen(context).onGetTemplate() as ListTemplate

        assertEquals(context.getString(R.string.app_name), template.title.toString())
        assertEquals(Action.TYPE_APP_ICON, template.headerAction!!.type)
        assertEquals(1, template.actionStrip!!.actions.size)
        assertEquals(6, rows(template).size)
    }

    @Test
    fun rowTitlesStayStableSoUpdatesCountAsRefreshes() {
        val screen = LiveDataScreen(carContext(CarAppApiLevels.LEVEL_8))
        val first = rows(screen.onGetTemplate() as ListTemplate).map { it.title.toString() }
        val second = rows(screen.onGetTemplate() as ListTemplate).map { it.title.toString() }
        assertEquals(first, second)
    }

    @Test
    fun dtcScreenAsksToConnectWhenOffline() {
        val context = carContext(CarAppApiLevels.LEVEL_8)
        val template = DtcScreen(context).onGetTemplate() as ListTemplate

        assertFalse(template.isLoading)
        assertEquals(Action.TYPE_BACK, template.header!!.startHeaderAction!!.type)
        assertEquals(context.getString(R.string.dtc_not_connected), rows(template)[0].title.toString())
    }
}
