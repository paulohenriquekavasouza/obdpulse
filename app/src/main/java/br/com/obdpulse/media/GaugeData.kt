package br.com.obdpulse.media

import br.com.obdpulse.obd.Format
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus

class GaugeData private constructor(
    val connected: Boolean,
    val status: String,
    val subtitle: String,
    val speed: Double?,
    val speedText: String,
    val kmpl: String,
    val cellALabel: String,
    val cellAValue: String,
    val cellBLabel: String,
    val cellBValue: String,
    val lastZeroTo100: String,
) {
    fun signature(): String = listOf(
        connected, status, subtitle, speedText, kmpl, cellAValue, cellBValue, lastZeroTo100,
    ).joinToString("|")

    companion object {
        const val EMPTY = "—"
        const val MAX_SPEED = 240.0

        private val ROTATION = listOf(
            "0C" to "ROTAÇÃO",
            "05" to "MOTOR",
            "77" to "INTERCOOLER",
            "0F" to "ADMISSÃO",
            "62" to "TORQUE",
            "0B" to "MAP",
        )

        fun from(state: ObdState, tick: Int = 0): GaugeData {
            val byKey = state.values.associateBy { it.key }
            fun value(key: String) = byKey[key]?.let { "${it.text} ${it.unit}".trim() } ?: EMPTY

            val available = ROTATION.filter { it.first in byKey }.ifEmpty { ROTATION.take(2) }
            val a = available[(tick * 2) % available.size]
            val b = available[(tick * 2 + 1) % available.size]

            val sub10 = state.trip.lastSub10Ms
            return GaugeData(
                connected = state.status == ObdStatus.CONNECTED,
                status = MediaContent.status(state),
                subtitle = if (state.status == ObdStatus.CONNECTED) subtitle(byKey, tick) else MediaContent.status(state),
                speed = byKey["0D"]?.value,
                speedText = byKey["0D"]?.text ?: EMPTY,
                kmpl = byKey[Keys.CONSUMPTION]?.text ?: EMPTY,
                cellALabel = a.second,
                cellAValue = value(a.first),
                cellBLabel = b.second,
                cellBValue = value(b.first),
                lastZeroTo100 = if (sub10 == null) EMPTY else "${Format.number(sub10 / 1000.0, 1)} s",
            )
        }

        private fun subtitle(byKey: Map<String, LiveValue>, tick: Int): String {
            fun value(key: String) = byKey[key]?.let { "${it.text} ${it.unit}".trim() } ?: EMPTY
            return when (tick % 3) {
                0 -> "Motor ${value("05")} · ${value("0C")}"
                1 -> "Turbo ${value(Keys.BOOST)} · Interc. ${value("77")}"
                else -> "Consumo ${value(Keys.CONSUMPTION)} · ${value("0D")}"
            }
        }
    }
}
