package br.com.obdpulse.trips

import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.obd.TripStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    private class Fake : TripStorage {
        val saved = mutableListOf<TripRecord>()
        override fun upsert(record: TripRecord) {
            saved += record
        }
    }

    private fun state(distance: Double, status: ObdStatus = ObdStatus.CONNECTED) = ObdState(
        status = status,
        trip = TripStats(distanceKm = distance, fuelUsedL = distance / 10.0, maxSpeed = 90.0, hardAccels = 1),
    )

    @Test
    fun savesACheckpointAfterTheIntervalAndTheFinalRecordAtTheEnd() {
        val storage = Fake()
        val recorder = TripRecorder(storage, minDistanceKm = 0.3, checkpointMs = 30_000L)

        recorder.onState(state(1.0), 1_000L, 5.0)
        recorder.onState(state(2.0), 20_000L, 5.0)
        assertTrue(storage.saved.isEmpty())

        recorder.onState(state(3.0), 31_000L, 5.0)
        assertEquals(1, storage.saved.size)
        assertEquals(1_000L, storage.saved[0].startMs)
        assertEquals(31_000L, storage.saved[0].endMs)
        assertEquals(3.0, storage.saved[0].distanceKm, 0.0)
        assertEquals(5.0, storage.saved[0].pricePerLiter, 0.0)

        recorder.finish(60_000L)
        assertEquals(2, storage.saved.size)
        assertEquals(storage.saved[0].id, storage.saved[1].id)
        assertEquals(60_000L, storage.saved[1].endMs)
    }

    @Test
    fun doesNotSaveTripsBelowTheMinimumDistance() {
        val storage = Fake()
        val recorder = TripRecorder(storage, minDistanceKm = 0.3, checkpointMs = 30_000L)
        recorder.onState(state(0.1), 0L, 5.0)
        recorder.onState(state(0.2), 40_000L, 5.0)
        recorder.finish(50_000L)
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun ignoresStatesThatAreNotConnected() {
        val storage = Fake()
        val recorder = TripRecorder(storage)
        recorder.onState(state(5.0, ObdStatus.DISCONNECTED), 0L, 5.0)
        recorder.onState(state(5.0, ObdStatus.INITIALIZING), 1_000L, 5.0)
        recorder.finish(2_000L)
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun aNewSessionStartsANewTrip() {
        val storage = Fake()
        val recorder = TripRecorder(storage)
        recorder.onState(state(2.0), 1_000L, 5.0)
        recorder.finish(5_000L)
        recorder.onState(state(3.0), 100_000L, 5.0)
        recorder.finish(200_000L)
        assertEquals(listOf(1_000L, 100_000L), storage.saved.map { it.startMs })
    }

    @Test
    fun finishWithoutAnySessionDoesNothing() {
        val storage = Fake()
        TripRecorder(storage).finish(1_000L)
        assertTrue(storage.saved.isEmpty())
    }
}
