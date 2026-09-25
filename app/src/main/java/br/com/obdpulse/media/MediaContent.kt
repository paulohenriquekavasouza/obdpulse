package br.com.obdpulse.media

import br.com.obdpulse.obd.Labels
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus

data class MediaEntry(val id: String, val title: String, val subtitle: String?, val browsable: Boolean = false)

data class NowPlaying(val title: String, val subtitle: String, val album: String)

object MediaContent {
    const val ROOT = "root"
    const val DASHBOARD = "dashboard"
    const val ALL = "all"
    const val DTC = "dtc"
    const val STATUS = "status"
    const val VALUE_PREFIX = "value:"
    private const val EMPTY = "—"

    fun children(parentId: String, state: ObdState, favorites: List<String>): List<MediaEntry> = when (parentId) {
        ROOT -> listOf(
            MediaEntry(DASHBOARD, "Painel", status(state), browsable = true),
            MediaEntry(ALL, "Todos", "${state.values.size} valores", browsable = true),
            MediaEntry(DTC, "Falhas", dtcSummary(state), browsable = true),
        )
        DASHBOARD -> buildList {
            add(MediaEntry(STATUS, "Status", status(state)))
            for (key in visibleFavorites(state, favorites)) {
                add(MediaEntry(VALUE_PREFIX + key, Labels.name(key), format(state.values.firstOrNull { it.key == key })))
            }
        }
        ALL -> state.values.map { MediaEntry(VALUE_PREFIX + it.key, it.name, format(it)) }
        DTC -> dtcEntries(state)
        else -> emptyList()
    }

    fun nowPlaying(state: ObdState, favorites: List<String>, focus: String?): NowPlaying {
        if (state.status != ObdStatus.CONNECTED) return NowPlaying("OBD Pulse", status(state), "")
        val byKey = state.values.associateBy { it.key }
        val keys = (listOfNotNull(focus) + favorites).distinct().filter { it in byKey }
        val title = keys.firstOrNull()?.let { "${Labels.short(it)} ${format(byKey[it])}" } ?: "OBD Pulse"
        val subtitle = keys.drop(1).take(2).joinToString(" · ") { "${Labels.short(it)} ${format(byKey[it])}" }
        return NowPlaying(title, subtitle.ifEmpty { status(state) }, "${status(state)} · ${dtcSummary(state)}")
    }

    fun stableNowPlaying(state: ObdState): NowPlaying =
        NowPlaying("OBD Pulse", status(state), dtcSummary(state))

    fun match(query: String?, keys: List<String>): String? {
        val wanted = normalize(query ?: return null)
        if (wanted.isBlank()) return null
        return keys.firstOrNull { key ->
            val short = normalize(Labels.short(key))
            val name = normalize(Labels.name(key))
            name.contains(wanted) || wanted.contains(short)
        }
    }

    private fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()

    fun status(state: ObdState): String = when (state.status) {
        ObdStatus.DISCONNECTED -> "Desconectado · toque em play para conectar"
        ObdStatus.CONNECTING -> "Conectando ao leitor…"
        ObdStatus.INITIALIZING -> "Comunicando com o veículo…"
        ObdStatus.CONNECTED -> if (state.milOn == true) "Conectado · luz de falha acesa" else "Conectado"
        ObdStatus.ERROR -> state.message ?: "Erro de comunicação"
    }

    fun dtcSummary(state: ObdState): String {
        if (state.dtcLoading) return "Lendo falhas…"
        val report = state.dtcs ?: return "Toque para ler as falhas"
        val codes = report.stored + report.pending + report.permanent
        return if (codes.isEmpty()) "Sem falhas" else codes.joinToString(prefix = "${codes.size} falha(s): ") { it.code }
    }

    private fun visibleFavorites(state: ObdState, favorites: List<String>): List<String> =
        if (state.status == ObdStatus.CONNECTED && state.values.isNotEmpty()) {
            favorites.filter { key -> state.values.any { it.key == key } }
        } else {
            favorites
        }

    private fun dtcEntries(state: ObdState): List<MediaEntry> {
        if (state.dtcLoading) return listOf(MediaEntry("$DTC:loading", "Lendo falhas…", null))
        val report = state.dtcs ?: return listOf(
            MediaEntry(
                "$DTC:hint",
                if (state.status == ObdStatus.CONNECTED) "Lendo falhas…" else "Conecte ao veículo para ler as falhas",
                null,
            ),
        )
        val entries = report.stored.map { MediaEntry("$DTC:${it.code}", it.code, "Armazenada · central ${it.ecu}") } +
            report.pending.map { MediaEntry("$DTC:${it.code}", it.code, "Pendente · central ${it.ecu}") } +
            report.permanent.map { MediaEntry("$DTC:${it.code}", it.code, "Permanente · central ${it.ecu}") }
        return entries.ifEmpty { listOf(MediaEntry("$DTC:none", "Nenhuma falha registrada", null)) }
    }

    private fun format(value: LiveValue?): String =
        value?.let { "${it.text} ${it.unit}".trim() } ?: EMPTY
}
