package br.com.obdpulse.media

import br.com.obdpulse.obd.Format
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus

class GaugeData private constructor(
    val connected: Boolean,
    val status: String,
    val speed: Double?,
    val speedText: String,
    val kmpl: String,
    val rpm: String,
    val coolant: String,
    val lastZeroTo100: String,
) {
    fun signature(): String =
        listOf(connected, status, speedText, kmpl, rpm, coolant, lastZeroTo100).joinToString("|")

    companion object {
        const val EMPTY = "—"
        const val MAX_SPEED = 240.0

        fun from(state: ObdState): GaugeData {
            val byKey = state.values.associateBy { it.key }
            fun text(key: String) = byKey[key]?.let { "${it.text} ${it.unit}".trim() } ?: EMPTY
            val sub10 = state.trip.lastSub10Ms
            return GaugeData(
                connected = state.status == ObdStatus.CONNECTED,
                status = MediaContent.status(state),
                speed = byKey["0D"]?.value,
                speedText = byKey["0D"]?.text ?: EMPTY,
                kmpl = byKey[Keys.CONSUMPTION]?.text ?: EMPTY,
                rpm = text("0C"),
                coolant = text("05"),
                lastZeroTo100 = if (sub10 == null) EMPTY else "${Format.number(sub10 / 1000.0, 1)} s",
            )
        }
    }
}
