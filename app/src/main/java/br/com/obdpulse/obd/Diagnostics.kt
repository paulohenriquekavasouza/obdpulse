package br.com.obdpulse.obd

object Diagnostics {

    fun report(state: ObdState): String = buildString {
        appendLine("OBD Pulse — diagnóstico")
        state.adapter?.let { appendLine("Leitor: $it") }
        state.protocol?.let { appendLine("Protocolo: $it") }
        appendLine("VIN: ${state.vin ?: "não informado"}")
        for (ecu in state.ecus) {
            val label = ecu.label?.let { " ($it)" }.orEmpty()
            appendLine("Central ${ecu.header}$label: ${ecu.supportedPids} parâmetros")
            appendLine("  Suportados: ${hexList(ecu.pids.filter { it % 0x20 != 0 })}")
            val undecoded = ecu.undecodedPids
            if (undecoded.isNotEmpty()) appendLine("  Sem decodificação: ${hexList(undecoded)}")
        }
        if (state.undecoded.isNotEmpty()) {
            appendLine("Valores brutos:")
            for (raw in state.undecoded) appendLine("  ${raw.ecu} PID ${"%02X".format(raw.pid)}: ${raw.hex}")
        }
        if (state.rawReplies.isNotEmpty()) {
            appendLine("Respostas de identificação:")
            for ((command, reply) in state.rawReplies) appendLine("  $command: $reply")
        }
        if (state.values.isNotEmpty()) {
            appendLine("Valores atuais:")
            for (value in state.values) appendLine("  ${value.name}: ${value.text} ${value.unit}".trimEnd())
        }
    }.trimEnd()

    private fun hexList(pids: List<Int>) = pids.joinToString(" ") { "%02X".format(it) }
}
