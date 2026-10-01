package br.com.obdpulse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsRefreshTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsToTheSafeMinimum() {
        assertEquals(Prefs.MIN_REFRESH_MS, Prefs.refreshMs(context))
    }

    @Test
    fun keepsTheChosenValue() {
        Prefs.saveRefreshMs(context, 1_000L)
        assertEquals(1_000L, Prefs.refreshMs(context))
    }

    @Test
    fun neverGoesBelowTheMinimum() {
        Prefs.saveRefreshMs(context, 50L)
        assertEquals(Prefs.MIN_REFRESH_MS, Prefs.refreshMs(context))
    }

    @Test
    fun optionsStartAtTheMinimumAndAscend() {
        val options = Prefs.REFRESH_OPTIONS_MS
        assertEquals(Prefs.MIN_REFRESH_MS, options.first())
        assertEquals(options.sorted(), options)
        assertTrue(options.all { it >= Prefs.MIN_REFRESH_MS })
    }
}
