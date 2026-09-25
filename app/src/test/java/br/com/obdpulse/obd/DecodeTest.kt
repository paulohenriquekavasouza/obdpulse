package br.com.obdpulse.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class DecodeTest {

    private fun decode(key: String, vararg bytes: Int) = Pids.byKey.getValue(key).decode(bytes)

    @Test
    fun decodesCommonPids() {
        assertEquals(1726.0, decode("0C", 0x1A, 0xF8), 0.001)
        assertEquals(83.0, decode("05", 0x7B), 0.001)
        assertEquals(70.2, decode("52", 0xB3), 0.1)
        assertEquals(14.5, decode("0E", 0x9D), 0.001)
        assertEquals(1.0, decode("44", 0x80, 0x00), 0.0001)
        assertEquals(123456.7, decode("A6", 0x00, 0x12, 0xD6, 0x87), 0.01)
        assertEquals(35.0, decode("23", 0x01, 0x5E), 0.001)
    }

    @Test
    fun formatsWithBrazilianLocale() {
        assertEquals("0,85", Format.number(0.85, 2))
        assertEquals("1726", Format.number(1726.0, 0))
    }

    @Test
    fun decodesDtcCodes() {
        assertEquals("P0133", Dtc.decode(0x01, 0x33))
        assertEquals("P0420", Dtc.decode(0x04, 0x20))
        assertEquals("C0123", Dtc.decode(0x41, 0x23))
        assertEquals("B1234", Dtc.decode(0x92, 0x34))
        assertEquals("U0100", Dtc.decode(0xC1, 0x00))
    }

    @Test
    fun parsesDtcResponses() {
        val messages = ElmParser.messages("7E806430201330420\r7E9024300")
        val codes = Dtc.parse(messages, 0x43)
        assertEquals(listOf(DtcCode("P0133", "7E8"), DtcCode("P0420", "7E8")), codes)
    }
}
