package br.com.obdpulse.obd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmParserTest {

    @Test
    fun parsesSingleFramesFromMultipleEcus() {
        val raw = "SEARCHING...\r7E8 06 41 00 BE 3F A8 13\r7E9 06 41 00 98 18 00 01\r\r"
        val messages = ElmParser.messages(raw)
        assertEquals(2, messages.size)
        assertEquals("7E8", messages[0].header)
        assertArrayEquals(intArrayOf(0x41, 0x00, 0xBE, 0x3F, 0xA8, 0x13), messages[0].data)
        assertEquals("7E9", messages[1].header)
    }

    @Test
    fun assemblesMultiFrameVin() {
        val raw = "7E81014490201394244\r7E82133363341315854\r7E82231323334353637"
        val messages = ElmParser.messages(raw)
        assertEquals(1, messages.size)
        assertEquals("9BD363A1XT1234567", ElmParser.ascii(messages[0].data, 3))
    }

    @Test
    fun ignoresErrorsAndNoise() {
        assertTrue(ElmParser.messages("NO DATA\r").isEmpty())
        assertTrue(ElmParser.messages("?\r").isEmpty())
        assertTrue(ElmParser.messages("UNABLE TO CONNECT\r").isEmpty())
        assertTrue(ElmParser.messages("CAN ERROR\r").isEmpty())
        assertTrue(ElmParser.messages("STOPPED\r").isEmpty())
    }

    @Test
    fun parsesExtendedHeaders() {
        val messages = ElmParser.messages("18DAF110 03 41 0D 3C")
        assertEquals(1, messages.size)
        assertEquals("18DAF110", messages[0].header)
        assertArrayEquals(intArrayOf(0x41, 0x0D, 0x3C), messages[0].data)
    }
}
