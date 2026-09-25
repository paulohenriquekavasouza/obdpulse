package br.com.obdpulse.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTrackerTest {

    private fun sample(vararg pairs: Pair<String, Double>): Map<String, LiveValue> =
        pairs.associate { (key, value) -> key to LiveValue(key, key, value, "", "", 0) }

    @Test
    fun tracksMaxima() {
        val tracker = TripTracker()
        tracker.onSample(0, sample(Keys.BOOST to 0.5, "0C" to 2000.0, "0D" to 50.0, "05" to 80.0, "77" to 40.0))
        tracker.onSample(1000, sample(Keys.BOOST to 1.2, "0C" to 5000.0, "0D" to 120.0, "05" to 95.0, "77" to 55.0))
        val stats = tracker.onSample(2000, sample(Keys.BOOST to 0.8, "0C" to 3000.0, "0D" to 60.0))

        assertEquals(1.2, stats.maxBoost!!, 0.0001)
        assertEquals(5000.0, stats.maxRpm!!, 0.0001)
        assertEquals(120.0, stats.maxSpeed!!, 0.0001)
        assertEquals(95.0, stats.maxCoolant!!, 0.0001)
        assertEquals(55.0, stats.maxIntake!!, 0.0001)
    }

    @Test
    fun averagesKmPerLiterFromSpeedAndFuelRate() {
        val tracker = TripTracker()
        tracker.onSample(0, sample("0D" to 90.0, "5E" to 9.0))
        val stats = tracker.onSample(1000, sample("0D" to 30.0, "5E" to 6.0))
        assertEquals((90.0 + 30.0) / (9.0 + 6.0), stats.avgKmPerLiter!!, 0.0001)
    }

    @Test
    fun measuresZeroTo100WithInterpolation() {
        val tracker = TripTracker()
        tracker.onSample(0, sample("0D" to 0.0))
        tracker.onSample(1000, sample("0D" to 0.0))
        tracker.onSample(1500, sample("0D" to 20.0))
        tracker.onSample(8000, sample("0D" to 95.0))
        val stats = tracker.onSample(9000, sample("0D" to 105.0))

        assertEquals(7475L, stats.bestZeroTo100Ms)
        assertEquals(7475L, stats.lastZeroTo100Ms)
        assertEquals(7475L, stats.lastSub10Ms)
    }

    @Test
    fun runsOfTenSecondsOrMoreDoNotCountAsSub10() {
        val tracker = TripTracker()
        tracker.onSample(0, sample("0D" to 0.0))
        tracker.onSample(1000, sample("0D" to 0.0))
        tracker.onSample(1001, sample("0D" to 10.0))
        tracker.onSample(12999, sample("0D" to 90.0))
        val stats = tracker.onSample(13000, sample("0D" to 100.0))
        assertEquals(12000L, stats.lastZeroTo100Ms)
        assertNull(stats.lastSub10Ms)
    }

    @Test
    fun keepsBestAndUpdatesLastAcrossRuns() {
        val tracker = TripTracker()
        run(tracker, from = 0, launchAt = 1000, reach = 9000)
        run(tracker, from = 20000, launchAt = 21000, reach = 30000)
        val stats = tracker.stats()
        assertEquals(8000L, stats.bestZeroTo100Ms)
        assertEquals(9000L, stats.lastZeroTo100Ms)
    }

    @Test
    fun ignoresRunsThatNeverReach100() {
        val tracker = TripTracker()
        tracker.onSample(0, sample("0D" to 0.0))
        tracker.onSample(1000, sample("0D" to 40.0))
        val stats = tracker.onSample(5000, sample("0D" to 80.0))
        assertNull(stats.bestZeroTo100Ms)
    }

    private fun run(tracker: TripTracker, from: Long, launchAt: Long, reach: Long) {
        tracker.onSample(from, sample("0D" to 0.0))
        tracker.onSample(launchAt, sample("0D" to 0.0))
        tracker.onSample(launchAt + 1, sample("0D" to 10.0))
        tracker.onSample(reach - 1, sample("0D" to 90.0))
        tracker.onSample(reach, sample("0D" to 100.0))
    }
}
