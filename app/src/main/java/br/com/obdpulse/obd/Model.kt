package br.com.obdpulse.obd

import java.io.IOException

enum class ObdStatus { DISCONNECTED, CONNECTING, INITIALIZING, CONNECTED, ERROR }

data class LiveValue(
    val key: String,
    val name: String,
    val value: Double,
    val text: String,
    val unit: String,
    val priority: Int,
)

data class DtcCode(val code: String, val ecu: String)

data class DtcReport(
    val stored: List<DtcCode>,
    val pending: List<DtcCode>,
    val permanent: List<DtcCode>,
)

data class EcuInfo(val header: String, val name: String?, val pids: List<Int>) {
    val supportedPids: Int get() = pids.count { it % 0x20 != 0 }
    val undecodedPids: List<Int> get() = pids.filter { it % 0x20 != 0 && it !in Pids.handled }
    val label: String? get() = name ?: EcuLabels.of(header)
}

data class RawPid(val ecu: String, val pid: Int, val hex: String)

data class TripStats(
    val maxBoost: Double? = null,
    val maxRpm: Double? = null,
    val maxSpeed: Double? = null,
    val maxCoolant: Double? = null,
    val maxIntake: Double? = null,
    val avgKmPerLiter: Double? = null,
    val bestZeroTo100Ms: Long? = null,
    val lastZeroTo100Ms: Long? = null,
)

data class ObdState(
    val status: ObdStatus = ObdStatus.DISCONNECTED,
    val message: String? = null,
    val adapter: String? = null,
    val protocol: String? = null,
    val protocolNumber: Char? = null,
    val vin: String? = null,
    val ecus: List<EcuInfo> = emptyList(),
    val values: List<LiveValue> = emptyList(),
    val milOn: Boolean? = null,
    val dtcCount: Int? = null,
    val dtcs: DtcReport? = null,
    val dtcLoading: Boolean = false,
    val dtcReadCount: Int = 0,
    val undecoded: List<RawPid> = emptyList(),
    val rawReplies: Map<String, String> = emptyMap(),
    val trip: TripStats = TripStats(),
) {
    val isActive: Boolean
        get() = status == ObdStatus.CONNECTING || status == ObdStatus.INITIALIZING || status == ObdStatus.CONNECTED
}

object EcuLabels {
    fun of(header: String): String? = when (header) {
        "7E8", "18DAF110" -> "motor"
        "7E9", "18DAF118" -> "câmbio"
        else -> null
    }
}

class ObdException(message: String) : Exception(message)

class ElmTimeoutException(command: String) : IOException("Sem resposta do leitor para $command")
