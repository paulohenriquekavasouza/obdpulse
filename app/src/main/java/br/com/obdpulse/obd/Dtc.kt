package br.com.obdpulse.obd

object Dtc {

    fun decode(a: Int, b: Int): String {
        val system = "PCBU"[a shr 6]
        return "%c%d%X%X%X".format(system, (a shr 4) and 0x03, a and 0x0F, b shr 4, b and 0x0F)
    }

    fun parse(messages: List<EcuMessage>, service: Int): List<DtcCode> {
        val codes = mutableListOf<DtcCode>()
        for (message in messages) {
            val data = message.data
            if (data.isEmpty() || data[0] != service) continue
            var i = 2
            while (i + 1 < data.size) {
                val a = data[i]
                val b = data[i + 1]
                if (a != 0 || b != 0) codes += DtcCode(decode(a, b), message.header)
                i += 2
            }
        }
        return codes.distinct()
    }
}
