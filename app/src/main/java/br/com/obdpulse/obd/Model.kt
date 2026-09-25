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

data class EcuInfo(val header: String, val name: String?, val supportedPids: Int)

data class ObdState(
    val status: ObdStatus = ObdStatus.DISCONNECTED,
    val message: String? = null,
    val adapter: String? = null,
    val protocol: String? = null,
    val vin: String? = null,
    val ecus: List<EcuInfo> = emptyList(),
    val values: List<LiveValue> = emptyList(),
    val milOn: Boolean? = null,
    val dtcCount: Int? = null,
    val dtcs: DtcReport? = null,
    val dtcLoading: Boolean = false,
    val dtcReadCount: Int = 0,
) {
    val isActive: Boolean
        get() = status == ObdStatus.CONNECTING || status == ObdStatus.INITIALIZING || status == ObdStatus.CONNECTED
}

class ObdException(message: String) : Exception(message)

class ElmTimeoutException(command: String) : IOException("Sem resposta do leitor para $command")
