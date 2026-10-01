package br.com.obdpulse.obd

enum class ProbeStatus { OK, NO_REPLY, NEGATIVE, MISMATCH }

data class ProbeResult(
    val command: String,
    val header: String,
    val payload: List<Int>,
    val rawHex: String,
    val status: ProbeStatus,
    val nrc: Int? = null,
    val adapterText: String = "",
)

data class Candidate(val label: String, val value: String)

object PidDecoder {

    const val SERVICE_LIVE = 0x01
    const val SERVICE_EXTENDED = 0x22

    fun parsePid(text: String): Int? =
        text.trim().removePrefix("0x").removePrefix("0X")
            .takeIf { it.isNotEmpty() && it.length <= 4 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
            ?.toInt(16)

    fun pidHex(pid: Int, service: Int): String =
        if (service == SERVICE_EXTENDED) "%04X".format(pid) else "%02X".format(pid)

    fun hex(bytes: List<Int>): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun candidates(bytes: List<Int>): List<Candidate> {
        if (bytes.isEmpty()) return emptyList()
        val a = bytes[0]
        val out = mutableListOf<Candidate>()
        out += Candidate("A bruto", a.toString())
        if (a >= 0x80) out += Candidate("A sinalizado", (a - 256).toString())
        out += Candidate("A − 40 (°C)", (a - 40).toString())
        out += Candidate("A × 100/255 (%)", Format.number(a * 100.0 / 255.0, 1))
        out += Candidate("(A−128) × 100/128 (%)", Format.number((a - 128) * 100.0 / 128.0, 1))
        out += Candidate("A ÷ 2 − 64 (°)", Format.number(a / 2.0 - 64.0, 1))
        if (bytes.size >= 2) {
            val b = bytes[1]
            val ab = a * 256 + b
            out += Candidate("AB bruto", ab.toString())
            if (ab >= 0x8000) out += Candidate("AB sinalizado", (ab - 65536).toString())
            out += Candidate("AB ÷ 4 (rpm)", Format.number(ab / 4.0, 0))
            out += Candidate("AB ÷ 100", Format.number(ab / 100.0, 2))
            out += Candidate("AB ÷ 1000", Format.number(ab / 1000.0, 3))
            out += Candidate("AB − 40", (ab - 40).toString())
            out += Candidate("AB × 100/255 (%)", Format.number(ab * 100.0 / 255.0, 1))
        }
        return out
    }
}
