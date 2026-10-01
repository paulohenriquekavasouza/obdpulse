package br.com.obdpulse.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripCodecTest {

    private fun record(
        start: Long = 1_000L,
        distance: Double = 12.5,
        fuel: Double = 1.25,
    ) = TripRecord(
        startMs = start,
        endMs = start + 600_000L,
        distanceKm = distance,
        fuelUsedL = fuel,
        maxSpeed = 98.0,
        maxRpm = 5400.0,
        maxBoost = 1.35,
        best0100Ms = 8_730L,
        hardAccels = 3,
        pricePerLiter = 5.89,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val original = record()
        assertEquals(original, TripCodec.decode(TripCodec.encode(original)))
    }

    @Test
    fun roundTripKeepsMissingOptionalFields() {
        val original = record().copy(maxSpeed = null, maxRpm = null, maxBoost = null, best0100Ms = null)
        val decoded = TripCodec.decode(TripCodec.encode(original))!!
        assertNull(decoded.maxSpeed)
        assertNull(decoded.maxRpm)
        assertNull(decoded.maxBoost)
        assertNull(decoded.best0100Ms)
        assertEquals(original, decoded)
    }

    @Test
    fun decodeAllSkipsInvalidLinesAndSortsByStart() {
        val text = listOf(
            TripCodec.encode(record(start = 3_000L)),
            "lixo;sem;formato",
            "",
            TripCodec.encode(record(start = 1_000L)),
            "1;2;abc;4;;;;;0;5.0",
        ).joinToString("\n")
        val result = TripCodec.decodeAll(text)
        assertEquals(listOf(1_000L, 3_000L), result.map { it.startMs })
    }

    @Test
    fun encodeAllAndDecodeAllAreInverse() {
        val list = listOf(record(start = 1L), record(start = 2L, distance = 3.0))
        assertEquals(list, TripCodec.decodeAll(TripCodec.encodeAll(list)))
    }
}
