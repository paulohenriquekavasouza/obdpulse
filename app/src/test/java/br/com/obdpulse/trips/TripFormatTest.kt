package br.com.obdpulse.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TripFormatTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private val record = TripRecord(
        startMs = 1_000_000_000_000L,
        endMs = 1_000_000_000_000L + 42 * 60_000L,
        distanceKm = 12.34,
        fuelUsedL = 1.0,
        maxSpeed = 98.0,
        maxRpm = 5400.0,
        maxBoost = 1.35,
        best0100Ms = 8_730L,
        hardAccels = 0,
        pricePerLiter = 5.89,
    )

    @Test
    fun durationUsesMinutesAndHours() {
        assertEquals("<1 min", TripFormat.duration(30_000L))
        assertEquals("42 min", TripFormat.duration(42 * 60_000L))
        assertEquals("1 h 05 min", TripFormat.duration(65 * 60_000L))
    }

    @Test
    fun datesUseTheGivenZone() {
        assertEquals("09/09/2001 01:46", TripFormat.date(1_000_000_000_000L, utc))
        assertEquals("09/09", TripFormat.shortDate(1_000_000_000_000L, utc))
        assertEquals("09/09/2001 01:46 · 42 min", TripFormat.title(record, utc))
    }

    @Test
    fun summaryLineShowsDistanceConsumptionCostAndScore() {
        val line = TripFormat.summaryLine(record)
        assertTrue(line.contains("12,3 km"))
        assertTrue(line.contains("12,3 km/L"))
        assertTrue(line.contains("R$ 5,89"))
        assertTrue(line.contains("nota 83"))
    }

    @Test
    fun summaryLineWithoutFuelShowsDashAndNoScore() {
        val line = TripFormat.summaryLine(record.copy(fuelUsedL = 0.0))
        assertTrue(line.contains("—"))
        assertTrue(!line.contains("nota"))
    }

    @Test
    fun detailListsAllFields() {
        val detail = TripFormat.detail(record)
        assertTrue(detail.contains("Distância: 12,3 km"))
        assertTrue(detail.contains("Combustível usado: 1,00 L"))
        assertTrue(detail.contains("Velocidade máxima: 98 km/h"))
        assertTrue(detail.contains("Melhor 0–100: 8,7 s"))
        assertTrue(detail.contains("Acelerações fortes: 0"))
    }

    @Test
    fun summaryFormatsTotals() {
        val text = TripFormat.summary(TripHistory.totals(listOf(record)))
        assertTrue(text.contains("Viagens: 1"))
        assertTrue(text.contains("Consumo médio: 12,3 km/L"))
    }
}
