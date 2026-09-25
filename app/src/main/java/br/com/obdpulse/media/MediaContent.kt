package br.com.obdpulse.media

import br.com.obdpulse.obd.Format
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.Labels
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus

data class MediaEntry(val id: String, val title: String, val subtitle: String?, val browsable: Boolean = false)

data class NowPlaying(val title: String, val subtitle: String, val album: String)

object MediaContent {
    const val ROOT = "root"
    const val DASHBOARD = "dashboard"
    const val PERFORMANCE = "performance"
    const val TURBO = "turbo"
    const val ALL = "all"
    const val DTC = "dtc"
    const val STATUS = "status"
    const val VALUE_PREFIX = "value:"
    private const val EMPTY = "—"

    val BROWSABLE_IDS = listOf(ROOT, DASHBOARD, PERFORMANCE, TURBO, ALL, DTC)

    fun children(parentId: String, state: ObdState, favorites: List<String>): List<MediaEntry> = when (parentId) {
        ROOT -> listOf(
            MediaEntry(DASHBOARD, "Painel", status(state), browsable = true),
            MediaEntry(PERFORMANCE, "Desempenho", performanceSummary(state), browsable = true),
            MediaEntry(TURBO, "Turbo", turboSummary(state), browsable = true),
            MediaEntry(DTC, "Falhas", dtcSummary(state), browsable = true),
        )
        DASHBOARD -> buildList {
            val keys = (listOf(Keys.CONSUMPTION) + visibleFavorites(state, favorites)).distinct()
            add(valueEntry(Keys.CONSUMPTION, state))
            add(MediaEntry(STATUS, "Status", status(state)))
            for (key in keys.drop(1)) add(valueEntry(key, state))
            add(MediaEntry(ALL, "Todos os parâmetros", "${state.values.size} valores", browsable = true))
        }
        PERFORMANCE -> performanceEntries(state)
        TURBO -> turboEntries(state)
        ALL -> state.values.map { MediaEntry(VALUE_PREFIX + it.key, it.name, format(it)) }
        DTC -> dtcEntries(state)
        else -> emptyList()
    }

    private fun performanceEntries(state: ObdState): List<MediaEntry> {
        val trip = state.trip
        return listOf(
            MediaEntry("perf:0100best", "0–100 km/h (melhor)", seconds(trip.bestZeroTo100Ms)),
            MediaEntry("perf:0100last", "0–100 km/h (última)", seconds(trip.lastZeroTo100Ms)),
            MediaEntry("perf:vmax", "Velocidade máxima", number(trip.maxSpeed, 0, "km/h")),
            MediaEntry("perf:rpmmax", "Rotação máxima", number(trip.maxRpm, 0, "rpm")),
            MediaEntry("perf:boostmax", "Turbo máximo", number(trip.maxBoost, 2, "bar")),
            MediaEntry("perf:coolmax", "Temp. do motor máxima", number(trip.maxCoolant, 0, "°C")),
            MediaEntry("perf:avgkmpl", "Média de km/L (viagem)", number(trip.avgKmPerLiter, 1, "km/L")),
        )
    }

    private fun turboEntries(state: ObdState): List<MediaEntry> {
        val byKey = state.values.associateBy { it.key }
        return listOf(
            MediaEntry("turbo:now", "Turbo atual", format(byKey[Keys.BOOST])),
            MediaEntry("turbo:max", "Turbo máximo", number(state.trip.maxBoost, 2, "bar")),
            MediaEntry("turbo:map", "Pressão no coletor (MAP)", format(byKey["0B"])),
            MediaEntry("turbo:baro", "Pressão barométrica", format(byKey["33"])),
            MediaEntry("turbo:intake", "Temp. do intercooler", format(byKey["77"])),
        )
    }

    private fun performanceSummary(state: ObdState): String {
        val best = seconds(state.trip.bestZeroTo100Ms)
        val vmax = number(state.trip.maxSpeed, 0, "km/h")
        return "0–100: $best · Vmáx: $vmax"
    }

    private fun turboSummary(state: ObdState): String {
        val now = format(state.values.firstOrNull { it.key == Keys.BOOST })
        val max = number(state.trip.maxBoost, 2, "bar")
        return "Atual: $now · Máx: $max"
    }

    private fun seconds(ms: Long?): String = if (ms == null) EMPTY else "${Format.number(ms / 1000.0, 1)} s"

    private fun number(value: Double?, decimals: Int, unit: String): String =
        if (value == null) EMPTY else "${Format.number(value, decimals)} $unit"

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

    private fun valueEntry(key: String, state: ObdState): MediaEntry =
        MediaEntry(VALUE_PREFIX + key, Labels.name(key), format(state.values.firstOrNull { it.key == key }))

    private fun format(value: LiveValue?): String =
        value?.let { "${it.text} ${it.unit}".trim() } ?: EMPTY
}
