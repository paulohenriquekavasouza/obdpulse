package br.com.obdpulse.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripHistoryTest {

    private fun record(start: Long, distance: Double, fuel: Double) = TripRecord(
        startMs = start,
        endMs = start + 60_000L,
        distanceKm = distance,
        fuelUsedL = fuel,
        maxSpeed = null,
        maxRpm = null,
        maxBoost = null,
        best0100Ms = null,
        hardAccels = 0,
        pricePerLiter = 6.0,
    )

    @Test
    fun upsertReplacesTheSameTripAndKeepsOrder() {
        val first = record(1L, 10.0, 1.0)
        val second = record(2L, 20.0, 2.0)
        val updated = first.copy(distanceKm = 11.0)
        val result = TripHistory.upsert(TripHistory.upsert(emptyList(), second), first)
        val replaced = TripHistory.upsert(result, updated)
        assertEquals(listOf(1L, 2L), replaced.map { it.id })
        assertEquals(11.0, replaced.first().distanceKm, 0.0)
    }

    @Test
    fun upsertKeepsOnlyTheNewestTrips() {
        var list = emptyList<TripRecord>()
        for (i in 1..(TripHistory.MAX_TRIPS + 5)) list = TripHistory.upsert(list, record(i.toLong(), 1.0, 0.1))
        assertEquals(TripHistory.MAX_TRIPS, list.size)
        assertEquals(6L, list.first().id)
    }

    @Test
    fun removeDropsOnlyTheRequestedTrip() {
        val list = listOf(record(1L, 1.0, 0.1), record(2L, 1.0, 0.1))
        assertEquals(listOf(2L), TripHistory.remove(list, 1L).map { it.id })
    }

    @Test
    fun totalsWeightConsumptionByFuelUsed() {
        val list = listOf(
            record(1L, 100.0, 10.0),
            record(2L, 50.0, 10.0),
            record(3L, 20.0, 0.0),
        )
        val totals = TripHistory.totals(list)
        assertEquals(3, totals.count)
        assertEquals(170.0, totals.distanceKm, 1e-9)
        assertEquals(20.0, totals.fuelUsedL, 1e-9)
        assertEquals(120.0, totals.cost, 1e-9)
        assertEquals(7.5, totals.avgKmPerLiter!!, 1e-9)
    }

    @Test
    fun totalsWithoutFuelDataHaveNoAverage() {
        val totals = TripHistory.totals(listOf(record(1L, 5.0, 0.0)))
        assertNull(totals.avgKmPerLiter)
        assertNull(totals.avgScore)
    }

    @Test
    fun tripWithoutFuelHasNoConsumption() {
        assertNull(record(1L, 5.0, 0.0).kmPerLiter)
        assertEquals(10.0, record(1L, 100.0, 10.0).kmPerLiter!!, 1e-9)
    }
}
