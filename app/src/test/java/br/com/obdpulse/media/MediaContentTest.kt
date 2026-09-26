package br.com.obdpulse.media

import br.com.obdpulse.obd.DtcCode
import br.com.obdpulse.obd.DtcReport
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.obd.TripStats
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
    fun rootHasFourBrowsableTabs() {
        val root = MediaContent.children(MediaContent.ROOT, connected, favorites)
        assertEquals(listOf("Painel", "Desempenho", "Turbo", "Falhas"), root.map { it.title })
        assertTrue(root.all { it.browsable })
        assertTrue(root.size <= 4)
    }

    @Test
    fun dashboardShowsConsumptionStatusAndOnlyMarkedFavorites() {
        val dashboard = MediaContent.children(MediaContent.DASHBOARD, connected, favorites)
        assertEquals(
            listOf("value:${Keys.CONSUMPTION}", "status", "value:BOOST", "value:0C", "value:0D", MediaContent.ALL),
            dashboard.map { it.id },
        )
        assertEquals("Consumo em km/L", dashboard[0].title)
        assertEquals("0,85 bar", dashboard[2].subtitle)
        assertTrue(dashboard.none { it.id == MediaContent.CLUSTER })
        assertTrue(dashboard.first { it.id == MediaContent.ALL }.browsable)
    }

    @Test
    fun dashboardOnlyShowsMarkedItems() {
        val marked = listOf(Keys.BOOST)
        val dashboard = MediaContent.children(MediaContent.DASHBOARD, connected, marked)
        assertEquals(
            listOf("value:${Keys.CONSUMPTION}", "status", "value:BOOST", MediaContent.ALL),
            dashboard.map { it.id },
        )
    }

    @Test
    fun dashboardKeepsAllFavoritesWhileDisconnected() {
        val dashboard = MediaContent.children(MediaContent.DASHBOARD, ObdState(), favorites)
        assertEquals("value:${Keys.CONSUMPTION}", dashboard[0].id)
        assertEquals("status", dashboard[1].id)
        assertEquals(MediaContent.ALL, dashboard.last().id)
        assertEquals(7, dashboard.size)
        assertEquals("—", dashboard[0].subtitle)
    }

    @Test
    fun allTabPinsConsumptionAndClusterThenOrdersRest() {
        val ordered = MediaContent.children(
            MediaContent.ALL, connected, favorites, order = listOf("0D", "0C"),
        )
        assertEquals(
            listOf("value:${Keys.CONSUMPTION}", MediaContent.CLUSTER, "value:0D", "value:0C", "value:BOOST"),
            ordered.map { it.id },
        )
        assertTrue(ordered[1].browsable)
    }

    @Test
    fun performanceTabIncludesLiveConsumptionFirst() {
        val entries = MediaContent.children(MediaContent.PERFORMANCE, connected, favorites)
        assertEquals("value:${Keys.CONSUMPTION}", entries.first().id)
    }

    @Test
    fun clusterTabMarksSelection() {
        val entries = MediaContent.children(MediaContent.CLUSTER, connected, favorites, clusterMetric = Keys.BOOST)
        val none = entries.first { it.id == "cluster:" }
        val boost = entries.first { it.id == "cluster:${Keys.BOOST}" }
        assertEquals("Tocar para selecionar", none.subtitle)
        assertEquals("Selecionado", boost.subtitle)
    }

    @Test
    fun performanceTabShowsTripStats() {
        val state = connected.copy(
            trip = TripStats(
                maxBoost = 1.32,
                maxRpm = 6480.0,
                maxSpeed = 187.0,
                maxCoolant = 98.0,
                avgKmPerLiter = 9.4,
                bestZeroTo100Ms = 8230,
                lastZeroTo100Ms = 8730,
            ),
        )
        val entries = MediaContent.children(MediaContent.PERFORMANCE, state, favorites).associate { it.title to it.subtitle }
        assertEquals("8,2 s", entries["0–100 km/h (melhor)"])
        assertEquals("8,7 s", entries["0–100 km/h (última)"])
        assertEquals("187 km/h", entries["Velocidade máxima"])
        assertEquals("6480 rpm", entries["Rotação máxima"])
        assertEquals("1,32 bar", entries["Turbo máximo"])
        assertEquals("9,4 km/L", entries["Média de km/L (viagem)"])
    }

    @Test
    fun performanceTabShowsDashesWhenEmpty() {
        val entries = MediaContent.children(MediaContent.PERFORMANCE, connected, favorites)
        assertEquals("—", entries.first { it.title == "0–100 km/h (melhor)" }.subtitle)
    }

    @Test
    fun turboTabShowsCurrentAndMax() {
        val state = connected.copy(
            values = connected.values + LiveValue("0B", "Pressão no coletor (MAP)", 210.0, "210", "kPa", 5),
            trip = TripStats(maxBoost = 1.32),
        )
        val entries = MediaContent.children(MediaContent.TURBO, state, favorites).associate { it.title to it.subtitle }
        assertEquals("0,85 bar", entries["Turbo atual"])
        assertEquals("1,32 bar", entries["Turbo máximo"])
        assertEquals("210 kPa", entries["Pressão no coletor (MAP)"])
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
