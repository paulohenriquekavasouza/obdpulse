package br.com.obdpulse.media

import br.com.obdpulse.obd.DtcCode
import br.com.obdpulse.obd.DtcReport
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaContentTest {

    private val favorites = listOf(Keys.BOOST, "0C", "5C", "0D")

    private val connected = ObdState(
        status = ObdStatus.CONNECTED,
        values = listOf(
            LiveValue(Keys.BOOST, "Pressão do turbo (calculada)", 0.85, "0,85", "bar", 0),
            LiveValue("0C", "Rotação do motor", 1726.0, "1726", "rpm", 10),
            LiveValue("0D", "Velocidade", 60.0, "60", "km/h", 20),
        ),
    )

    @Test
    fun rootOnlyHasBrowsableTabs() {
        val root = MediaContent.children(MediaContent.ROOT, connected, favorites)
        assertEquals(listOf("Painel", "Todos", "Falhas"), root.map { it.title })
        assertTrue(root.all { it.browsable })
        assertTrue(root.size <= 4)
    }

    @Test
    fun dashboardPutsConsumptionFirstThenStatusAndFavorites() {
        val dashboard = MediaContent.children(MediaContent.DASHBOARD, connected, favorites)
        assertEquals(
            listOf("value:${Keys.CONSUMPTION}", "status", "value:BOOST", "value:0C", "value:0D"),
            dashboard.map { it.id },
        )
        assertEquals("Consumo em km/L", dashboard[0].title)
        assertEquals("0,85 bar", dashboard[2].subtitle)
        assertTrue(dashboard.none { it.browsable })
    }

    @Test
    fun dashboardKeepsAllFavoritesWhileDisconnected() {
        val dashboard = MediaContent.children(MediaContent.DASHBOARD, ObdState(), favorites)
        assertEquals("value:${Keys.CONSUMPTION}", dashboard[0].id)
        assertEquals("status", dashboard[1].id)
        assertEquals(6, dashboard.size)
        assertEquals("—", dashboard[0].subtitle)
    }

    @Test
    fun nowPlayingHighlightsFocusedValue() {
        val nowPlaying = MediaContent.nowPlaying(connected, favorites, focus = "0D")
        assertEquals("Velocidade 60 km/h", nowPlaying.title)
        assertEquals("Turbo 0,85 bar · Rotação 1726 rpm", nowPlaying.subtitle)
        assertEquals("Conectado · Toque para ler as falhas", nowPlaying.album)
    }

    @Test
    fun stableNowPlayingHasNoLiveValues() {
        val nowPlaying = MediaContent.stableNowPlaying(connected)
        assertEquals("OBD Pulse", nowPlaying.title)
        assertEquals("Conectado", nowPlaying.subtitle)
    }

    @Test
    fun nowPlayingWhileDisconnectedExplainsHowToConnect() {
        val nowPlaying = MediaContent.nowPlaying(ObdState(), favorites, focus = null)
        assertEquals("OBD Pulse", nowPlaying.title)
        assertEquals("Desconectado · toque em play para conectar", nowPlaying.subtitle)
    }

    @Test
    fun voiceSearchMatchesParameters() {
        val keys = listOf(Keys.BOOST, "0C", "77", "05", Keys.CONSUMPTION)
        assertEquals(Keys.BOOST, MediaContent.match("turbo", keys))
        assertEquals("0C", MediaContent.match("Rotação", keys))
        assertEquals("0C", MediaContent.match("rotacao do motor", keys))
        assertEquals("77", MediaContent.match("intercooler", keys))
        assertEquals(Keys.CONSUMPTION, MediaContent.match("consumo", keys))
        assertEquals(null, MediaContent.match("música", keys))
        assertEquals(null, MediaContent.match("", keys))
    }

    @Test
    fun dtcTabListsCodes() {
        val state = connected.copy(
            dtcs = DtcReport(listOf(DtcCode("P0133", "18DAF110")), listOf(DtcCode("P0420", "18DAF110")), emptyList()),
        )
        val entries = MediaContent.children(MediaContent.DTC, state, favorites)
        assertEquals(listOf("P0133", "P0420"), entries.map { it.title })
        assertEquals("Armazenada · central 18DAF110", entries[0].subtitle)
        assertEquals("2 falha(s): P0133, P0420", MediaContent.dtcSummary(state))
    }
}
