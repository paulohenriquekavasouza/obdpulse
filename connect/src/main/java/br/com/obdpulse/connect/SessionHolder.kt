package br.com.obdpulse.connect

object SessionHolder {
    @Volatile var session: UconnectSession? = null
    @Volatile var pin: String? = null

    fun clear() {
        session = null
        pin = null
    }
}
