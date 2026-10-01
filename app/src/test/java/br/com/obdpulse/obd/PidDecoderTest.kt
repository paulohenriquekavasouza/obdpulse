package br.com.obdpulse.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PidDecoderTest {

    @Test
    fun parsesHexPids() {
        assertEquals(0x1C, PidDecoder.parsePid("1C"))
        assertEquals(0x1C, PidDecoder.parsePid(" 1c "))
        assertEquals(0x1956, PidDecoder.parsePid("0x1956"))
        assertEquals(0x1956, PidDecoder.parsePid("1956"))
    }

    @Test
    fun rejectsInvalidPids() {
        assertNull(PidDecoder.parsePid(""))
        assertNull(PidDecoder.parsePid("GG"))
        assertNull(PidDecoder.parsePid("12345"))
    }

    @Test
    fun formatsPidHexByService() {
        assertEquals("05", PidDecoder.pidHex(0x05, PidDecoder.SERVICE_LIVE))
        assertEquals("1C", PidDecoder.pidHex(0x1C, PidDecoder.SERVICE_LIVE))
        assertEquals("1956", PidDecoder.pidHex(0x1956, PidDecoder.SERVICE_EXTENDED))
    }

    @Test
    fun singleByteCandidates() {
        val map = PidDecoder.candidates(listOf(0x50)).associate { it.label to it.value }
        assertEquals("80", map["A bruto"])
        assertEquals("40", map["A − 40 (°C)"])
        assertTrue(map.containsKey("A × 100/255 (%)"))
    }

    @Test
    fun twoByteCandidates() {
        val map = PidDecoder.candidates(listOf(0x1A, 0x2B)).associate { it.label to it.value }
        assertEquals("6699", map["AB bruto"])
        assertEquals("1675", map["AB ÷ 4 (rpm)"])
        assertEquals("6659", map["AB − 40"])
    }

    @Test
    fun emptyPayloadHasNoCandidates() {
        assertTrue(PidDecoder.candidates(emptyList()).isEmpty())
    }
}
