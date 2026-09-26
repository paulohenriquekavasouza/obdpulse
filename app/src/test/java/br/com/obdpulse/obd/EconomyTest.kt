package br.com.obdpulse.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {

    @Test
    fun scoreIsNullWithoutAverage() {
        assertNull(Economy.score(null, 0, 10.0))
    }

    @Test
    fun scoreScalesWithEfficiency() {
        val low = Economy.score(4.0, 0, 10.0)!!
        val high = Economy.score(14.0, 0, 10.0)!!
        assertEquals(0, low)
        assertEquals(100, high)
    }

    @Test
    fun hardAccelsReduceScore() {
        val clean = Economy.score(10.0, 0, 10.0)!!
        val aggressive = Economy.score(10.0, 20, 10.0)!!
        assertTrue(aggressive < clean)
    }

    @Test
    fun rangeUsesTankLevelAndAverage() {
        val range = Economy.rangeKm(50.0, 40.0, 12.0)!!
        assertEquals(20.0 * 12.0, range, 0.0001)
    }

    @Test
    fun rangeIsNullWithoutData() {
        assertNull(Economy.rangeKm(null, 40.0, 12.0))
        assertNull(Economy.rangeKm(50.0, 40.0, null))
        assertNull(Economy.rangeKm(50.0, 40.0, 0.0))
    }
}
