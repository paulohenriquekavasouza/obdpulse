package br.com.obdpulse.obd

class EcuMessage(val header: String, val data: IntArray)

object ElmParser {

    fun textLines(raw: String): List<String> =
        raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() && it != ">" }

    fun cleanLines(raw: String): List<String> =
        textLines(raw)
            .map { it.replace(" ", "").uppercase() }
            .filterNot { it.startsWith("SEARCHING") || it.startsWith("BUSINIT") }

    fun messages(raw: String): List<EcuMessage> = assemble(cleanLines(raw))

    fun assemble(lines: List<String>): List<EcuMessage> {
        val result = mutableListOf<EcuMessage>()
        val pending = LinkedHashMap<String, Pending>()
        for (line in lines) {
            if (!isHex(line)) continue
            val headerLength = if (line.length >= 12 && line.startsWith("18DA")) 8 else 3
            if (line.length < headerLength + 2) continue
            val header = line.substring(0, headerLength)
            val body = line.substring(headerLength)
            if (body.length % 2 != 0) continue
            val bytes = IntArray(body.length / 2) { body.substring(it * 2, it * 2 + 2).toInt(16) }
            val pci = bytes[0]
            when (pci shr 4) {
                0 -> {
                    val length = pci and 0x0F
                    if (length in 1 until bytes.size) {
                        result += EcuMessage(header, bytes.copyOfRange(1, 1 + length))
                    }
                }
                1 -> {
                    if (bytes.size < 2) continue
                    val length = ((pci and 0x0F) shl 8) or bytes[1]
                    pending[header] = Pending(length, bytes.copyOfRange(2, bytes.size).toMutableList())
                }
                2 -> {
                    val current = pending[header] ?: continue
                    for (i in 1 until bytes.size) current.data += bytes[i]
                    if (current.data.size >= current.length) {
                        result += EcuMessage(header, current.data.take(current.length).toIntArray())
                        pending.remove(header)
                    }
                }
            }
        }
        return result
    }

    fun ascii(data: IntArray, from: Int): String =
        data.drop(from).filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("").trim()

    private fun isHex(line: String) = line.isNotEmpty() && line.all { it in '0'..'9' || it in 'A'..'F' }

    private class Pending(val length: Int, val data: MutableList<Int>)
}
