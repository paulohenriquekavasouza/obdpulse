package br.com.obdpulse.obd

import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue

class FakeLink(private val responder: (String) -> String) {
    private val queue = LinkedBlockingQueue<Int>()

    val input: InputStream = object : InputStream() {
        override fun read(): Int = queue.take()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val first = queue.take()
            if (first < 0) return -1
            b[off] = first.toByte()
            var count = 1
            while (count < len) {
                val next = queue.peek() ?: break
                if (next < 0) break
                queue.poll()
                b[off + count] = next.toByte()
                count++
            }
            return count
        }
    }

    val output: OutputStream = object : OutputStream() {
        private val line = StringBuilder()

        override fun write(b: Int) {
            val c = (b and 0xFF).toChar()
            if (c == '\r') {
                val reply = responder(line.toString())
                line.setLength(0)
                "$reply\r\r>".forEach { queue.put(it.code) }
            } else {
                line.append(c)
            }
        }
    }

    fun close() = queue.put(-1)
}

class FakeCar {
    class Ecu(val response: String, val request: String, val name: String, val pids: Map<Int, IntArray>)

    val ecus = listOf(
        Ecu(
            "7E8", "7E0", "ECM-EngineControl",
            mapOf(
                0x01 to intArrayOf(0x00, 0x07, 0xE5, 0x00),
                0x05 to intArrayOf(0x7B),
                0x0B to intArrayOf(180),
                0x0C to intArrayOf(0x1A, 0xF8),
                0x0D to intArrayOf(60),
                0x0F to intArrayOf(0x46),
                0x33 to intArrayOf(95),
                0x52 to intArrayOf(0xB3),
                0x5C to intArrayOf(0x82),
            ),
        ),
        Ecu("7E9", "7E1", "TCM-TransmissionCtrl", mapOf(0x46 to intArrayOf(0x41))),
    )

    val vin = "9BD363A1XT1234567"
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var header = "7DF"

    fun respond(command: String): String {
        log += command
        return when {
            command == "ATZ" -> "\r\rELM327 v2.1"
            command == "ATDP" -> "ISO 15765-4 (CAN 11/500)"
            command == "ATRV" -> "12.4V"
            command.startsWith("ATSH") -> {
                header = command.removePrefix("ATSH")
                "OK"
            }
            command.startsWith("AT") -> "OK"
            else -> obd(command)
        }
    }

    private fun obd(command: String): String {
        val mode = command.substring(0, 2).toInt(16)
        val targets = if (header == "7DF") ecus else ecus.filter { it.request == header }
        val lines = mutableListOf<String>()
        for (ecu in targets) {
            val payload = when (mode) {
                0x01 -> {
                    val pid = command.substring(2, 4).toInt(16)
                    if (pid % 0x20 == 0) supportPage(ecu, pid) else ecu.pids[pid]?.let { intArrayOf(0x41, pid) + it }
                }
                0x03 -> if (ecu.response == "7E8") intArrayOf(0x43, 0x02, 0x01, 0x33, 0x04, 0x20) else intArrayOf(0x43, 0x00)
                0x07 -> intArrayOf(0x47, 0x00)
                0x09 -> when (command.substring(2, 4)) {
                    "02" -> if (ecu.response == "7E8") intArrayOf(0x49, 0x02, 0x01) + ascii(vin) else null
                    "0A" -> intArrayOf(0x49, 0x0A, 0x01) + ascii(ecu.name.padEnd(20, '\u0000'))
                    else -> null
                }
                else -> null
            }
            if (payload != null) lines += frames(ecu.response, payload)
        }
        return if (lines.isEmpty()) "NO DATA" else lines.joinToString("\r")
    }

    private fun supportPage(ecu: Ecu, base: Int): IntArray? {
        if (base != 0 && ecu.pids.keys.none { it > base }) return null
        val bytes = IntArray(4)
        for (pid in base + 1..base + 0x20) {
            val supported = pid in ecu.pids || (pid == base + 0x20 && ecu.pids.keys.any { it > pid })
            if (supported) {
                val i = pid - base - 1
                bytes[i / 8] = bytes[i / 8] or (0x80 shr (i % 8))
            }
        }
        return intArrayOf(0x41, base) + bytes
    }

    private fun ascii(text: String) = text.map { it.code }.toIntArray()

    private fun hex(bytes: List<Int>) = bytes.joinToString("") { "%02X".format(it) }

    private fun frames(header: String, payload: IntArray): List<String> {
        if (payload.size <= 7) return listOf(header + "%02X".format(payload.size) + hex(payload.toList()))
        val result = mutableListOf(header + "1%03X".format(payload.size) + hex(payload.take(6)))
        var index = 6
        var sequence = 1
        while (index < payload.size) {
            val chunk = payload.drop(index).take(7)
            result += header + "2%X".format(sequence and 0x0F) + hex(chunk)
            index += 7
            sequence++
        }
        return result
    }
}
