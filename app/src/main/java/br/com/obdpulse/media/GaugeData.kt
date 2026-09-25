package br.com.obdpulse.media

import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus

class GaugeData private constructor(
    val connected: Boolean,
    val status: String,
    val boost: Double?,
    val boostText: String,
    val boostMax: Double?,
    val kmpl: String,
    val rpm: String,
    val speed: String,
    val coolant: String,
) {
    fun signature(): String =
        listOf(connected, status, boostText, kmpl, rpm, speed, coolant).joinToString("|")

    companion object {
        const val EMPTY = "—"

        fun from(state: ObdState): GaugeData {
            val byKey = state.values.associateBy { it.key }
            fun text(key: String) = byKey[key]?.let { "${it.text} ${it.unit}".trim() } ?: EMPTY
            return GaugeData(
                connected = state.status == ObdStatus.CONNECTED,
                status = MediaContent.status(state),
                boost = byKey[Keys.BOOST]?.value,
                boostText = byKey[Keys.BOOST]?.text ?: EMPTY,
                boostMax = state.trip.maxBoost,
                kmpl = byKey[Keys.CONSUMPTION]?.text ?: EMPTY,
                rpm = text("0C"),
                speed = text("0D"),
                coolant = text("05"),
            )
        }
    }
}
