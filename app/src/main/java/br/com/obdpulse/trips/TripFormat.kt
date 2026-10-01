package br.com.obdpulse.trips

import br.com.obdpulse.obd.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TripFormat {

    const val EMPTY = "—"

    private val BR: Locale = Locale.forLanguageTag("pt-BR")

    fun date(ms: Long, zone: TimeZone = TimeZone.getDefault()): String = format("dd/MM/yyyy HH:mm", ms, zone)

    fun shortDate(ms: Long, zone: TimeZone = TimeZone.getDefault()): String = format("dd/MM", ms, zone)

    fun duration(ms: Long): String {
        val minutes = ms / 60_000L
        return when {
            minutes < 1 -> "<1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h " + String.format(Locale.US, "%02d", minutes % 60) + " min"
        }
    }

    fun title(record: TripRecord, zone: TimeZone = TimeZone.getDefault()): String =
        "${date(record.startMs, zone)} · ${duration(record.durationMs)}"

    fun summaryLine(record: TripRecord): String {
        val parts = mutableListOf(
            "${Format.number(record.distanceKm, 1)} km",
            record.kmPerLiter?.let { "${Format.number(it, 1)} km/L" } ?: EMPTY,
            money(record.cost),
        )
        record.ecoScore?.let { parts += "nota $it" }
        return parts.joinToString(" · ")
    }

    fun detail(record: TripRecord): String = listOf(
        "Distância: ${Format.number(record.distanceKm, 1)} km",
        "Duração: ${duration(record.durationMs)}",
        "Combustível usado: ${Format.number(record.fuelUsedL, 2)} L",
        "Consumo médio: ${record.kmPerLiter?.let { "${Format.number(it, 1)} km/L" } ?: EMPTY}",
        "Custo: ${money(record.cost)} (${money(record.pricePerLiter)}/L)",
        "Nota de condução: ${record.ecoScore?.let { "$it / 100" } ?: EMPTY}",
        "Velocidade máxima: ${record.maxSpeed?.let { "${Format.number(it, 0)} km/h" } ?: EMPTY}",
        "Rotação máxima: ${record.maxRpm?.let { "${Format.number(it, 0)} rpm" } ?: EMPTY}",
        "Turbo máximo: ${record.maxBoost?.let { "${Format.number(it, 2)} bar" } ?: EMPTY}",
        "Melhor 0–100: ${record.best0100Ms?.let { "${Format.number(it / 1000.0, 1)} s" } ?: EMPTY}",
        "Acelerações fortes: ${record.hardAccels}",
    ).joinToString("\n")

    fun summary(totals: TripTotals): String = listOf(
        "Viagens: ${totals.count}",
        "Distância total: ${Format.number(totals.distanceKm, 1)} km",
        "Combustível: ${Format.number(totals.fuelUsedL, 1)} L",
        "Custo: ${money(totals.cost)}",
        "Consumo médio: ${totals.avgKmPerLiter?.let { "${Format.number(it, 1)} km/L" } ?: EMPTY}",
        "Nota média: ${totals.avgScore?.let { "$it / 100" } ?: EMPTY}",
    ).joinToString("\n")

    private fun money(value: Double): String = "R$ ${Format.number(value, 2)}"

    private fun format(pattern: String, ms: Long, zone: TimeZone): String {
        val formatter = SimpleDateFormat(pattern, BR)
        formatter.timeZone = zone
        return formatter.format(Date(ms))
    }
}
