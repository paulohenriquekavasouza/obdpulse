package br.com.obdpulse.obd

data class ScanRow(
    val ecu: String,
    val label: String?,
    val service: Int,
    val pid: Int,
    val bytes: List<Int>,
)

data class EcuPing(
    val txId: Int,
    val responder: String?,
    val note: String?,
) {
    val present: Boolean get() = responder != null
}

data class ScanDiag(
    val ecu: String,
    val label: String?,
    val tried: Int,
    val ok: Int,
    val negative: Int,
    val noData: Int,
    val noReply: Int,
    val samples: List<String>,
    val sessionNote: String? = null,
)

object ScanReport {

    fun formatEcus(pings: List<EcuPing>, vin: String?): String {
        val builder = StringBuilder()
        builder.append("OBD Pulse — varredura de ECUs\n")
        vin?.let { builder.append("VIN: ").append(it).append('\n') }
        val present = pings.filter { it.present }
        builder.append("Centrais que responderam: ").append(present.size).append('\n')
        if (present.isEmpty()) {
            builder.append("nenhuma\n")
            return builder.toString()
        }
        for (ping in present) {
            builder.append('\n').append(ping.responder)
            EcuLabels.of(ping.responder ?: "")?.let { builder.append(" (").append(it).append(')') }
            ping.note?.let { builder.append("\n  ").append(it) }
            builder.append('\n')
        }
        return builder.toString()
    }

    fun format(rows: List<ScanRow>, diags: List<ScanDiag>, vin: String?): String {
        val builder = StringBuilder()
        builder.append("OBD Pulse — varredura de PIDs\n")
        vin?.let { builder.append("VIN: ").append(it).append('\n') }

        builder.append("\n== Parâmetros encontrados ==\n")
        if (rows.isEmpty()) {
            builder.append("nenhum\n")
        } else {
            for ((ecu, group) in rows.groupBy { it.ecu }) {
                val label = group.first().label
                builder.append(ecu)
                if (label != null) builder.append(" (").append(label).append(')')
                builder.append('\n')
                for (row in group) {
                    val command = "%02X".format(row.service) + PidDecoder.pidHex(row.pid, row.service)
                    val candidates = PidDecoder.candidates(row.bytes).joinToString(" · ") { "${it.label}=${it.value}" }
                    builder.append("  ").append(command).append(" = ").append(PidDecoder.hex(row.bytes))
                    if (candidates.isNotEmpty()) builder.append("  |  ").append(candidates)
                    builder.append('\n')
                }
            }
        }

        if (diags.isNotEmpty()) {
            builder.append("\n== Diagnóstico Modo 22 por central ==\n")
            for (d in diags) {
                builder.append(d.ecu)
                if (d.label != null) builder.append(" (").append(d.label).append(')')
                d.sessionNote?.let { builder.append("\n  sessão estendida: ").append(it) }
                builder.append("\n  ").append("tentados ").append(d.tried)
                    .append(", ok ").append(d.ok)
                    .append(", negativos ").append(d.negative)
                    .append(", sem dados ").append(d.noData)
                    .append(", sem resposta ").append(d.noReply).append('\n')
                for (sample in d.samples) builder.append("    ").append(sample).append('\n')
            }
        }
        return builder.toString()
    }
}
