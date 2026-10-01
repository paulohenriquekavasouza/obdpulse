package br.com.obdpulse.obd

import org.junit.Assert.assertTrue
import org.junit.Test

class ScanReportTest {

    @Test
    fun emptyReportStatesNothingFound() {
        val text = ScanReport.format(emptyList(), emptyList(), null)
        assertTrue(text.contains("nenhum"))
    }

    @Test
    fun groupsRowsByEcuWithCommandRawAndCandidates() {
        val rows = listOf(
            ScanRow("18DAF110", "motor", PidDecoder.SERVICE_LIVE, 0x1C, listOf(0x06)),
            ScanRow("18DAF118", "câmbio", PidDecoder.SERVICE_EXTENDED, 0x1956, listOf(0x0A, 0x3C)),
        )
        val text = ScanReport.format(rows, emptyList(), "9BD12345")
        assertTrue(text.contains("VIN: 9BD12345"))
        assertTrue(text.contains("18DAF110 (motor)"))
        assertTrue(text.contains("011C = 06"))
        assertTrue(text.contains("18DAF118 (câmbio)"))
        assertTrue(text.contains("221956 = 0A 3C"))
        assertTrue(text.contains("AB bruto=2620"))
    }

    @Test
    fun includesPerEcuDiagnostics() {
        val diags = listOf(
            ScanDiag("18DAF118", "câmbio", 289, 0, 0, 289, 0, listOf("F190: NO DATA"), sessionNote = "aberta"),
        )
        val text = ScanReport.format(emptyList(), diags, null)
        assertTrue(text.contains("Diagnóstico Modo 22"))
        assertTrue(text.contains("18DAF118 (câmbio)"))
        assertTrue(text.contains("tentados 289"))
        assertTrue(text.contains("sem dados 289"))
        assertTrue(text.contains("sessão estendida: aberta"))
        assertTrue(text.contains("F190: NO DATA"))
    }
}
