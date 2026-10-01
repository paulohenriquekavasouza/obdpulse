package br.com.obdpulse

import android.bluetooth.BluetoothManager
import android.content.Context
import br.com.obdpulse.bt.BluetoothLink
import br.com.obdpulse.obd.Diagnostics
import br.com.obdpulse.obd.Elm327
import br.com.obdpulse.obd.ElmTimeoutException
import br.com.obdpulse.obd.ObdEngine
import br.com.obdpulse.obd.ObdException
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

object ObdManager {

    const val DEFAULT_PROBE_TIMEOUT = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ObdState())
    val state: StateFlow<ObdState> = _state.asStateFlow()

    @Volatile private var job: Job? = null
    @Volatile private var engine: ObdEngine? = null
    @Volatile private var link: BluetoothLink? = null

    @Synchronized
    fun connect(context: Context, address: String) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        _state.value = ObdState(status = ObdStatus.CONNECTING)
        job = scope.launch { runSession(appContext, address) }
    }

    @Synchronized
    fun disconnect() {
        job?.cancel()
        job = null
        link?.close()
        _state.value = ObdState(status = ObdStatus.DISCONNECTED)
    }

    fun requestDtcs(): Boolean {
        val current = engine ?: return false
        if (_state.value.status != ObdStatus.CONNECTED) return false
        scope.launch { current.readDtcs() }
        return true
    }

    suspend fun probe(
        responseHeader: String,
        service: Int,
        pid: Int,
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT,
    ): br.com.obdpulse.obd.ProbeResult? {
        val current = engine ?: return null
        if (_state.value.status != ObdStatus.CONNECTED) return null
        return withContext(Dispatchers.IO) { current.probe(responseHeader, service, pid, timeoutMs) }
    }

    suspend fun refreshUndecoded() {
        val current = engine ?: return
        if (_state.value.status != ObdStatus.CONNECTED) return
        withContext(Dispatchers.IO) { current.readUndecoded() }
    }

    suspend fun startSession(responseHeader: String, sub: Int): br.com.obdpulse.obd.ProbeResult? {
        val current = engine ?: return null
        if (_state.value.status != ObdStatus.CONNECTED) return null
        return withContext(Dispatchers.IO) { current.startSession(responseHeader, sub) }
    }

    suspend fun testerPresent(responseHeader: String) {
        val current = engine ?: return
        if (_state.value.status != ObdStatus.CONNECTED) return
        withContext(Dispatchers.IO) { current.testerPresent(responseHeader) }
    }

    suspend fun setFastScanTiming(fast: Boolean) {
        val current = engine ?: return
        if (_state.value.status != ObdStatus.CONNECTED) return
        withContext(Dispatchers.IO) { current.setFastScanTiming(fast) }
    }

    fun extendedAddressing(): Boolean = engine?.extendedAddressing ?: false

    suspend fun pingEcu(txId: Int): br.com.obdpulse.obd.EcuPing? {
        val current = engine ?: return null
        if (_state.value.status != ObdStatus.CONNECTED) return null
        return withContext(Dispatchers.IO) { current.pingEcu(txId) }
    }

    suspend fun diagnosticsReport(): String {
        val current = engine
        if (current != null && current.state.value.status == ObdStatus.CONNECTED) {
            try {
                withContext(Dispatchers.IO) { current.readUndecoded() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            return Diagnostics.report(current.state.value)
        }
        return Diagnostics.report(_state.value)
    }

    private suspend fun runSession(context: Context, address: String) {
        var opened: BluetoothLink? = null
        var session: ObdEngine? = null
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw ObdException("Este aparelho não tem Bluetooth.")
            if (!adapter.isEnabled) throw ObdException("O Bluetooth está desligado.")
            val connection = BluetoothLink.open(adapter, address)
            opened = connection
            link = connection
            _state.update { it.copy(status = ObdStatus.INITIALIZING) }
            coroutineScope {
                val elm = Elm327(connection.input, connection.output)
                elm.start(this)
                val current = ObdEngine(elm)
                session = current
                engine = current
                launch {
                    current.state.collect {
                        if (isActive) {
                            _state.value = it
                            Prefs.updateRecords(context, it.trip)
                        }
                    }
                }
                try {
                    current.initialize(Prefs.protocol(context))
                    current.state.value.protocolNumber?.let { Prefs.saveProtocol(context, it) }
                    current.pollLoop()
                } finally {
                    connection.close()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(status = ObdStatus.ERROR, message = describe(e)) }
        } finally {
            opened?.close()
            clearIfCurrent(opened, session)
        }
    }

    @Synchronized
    private fun clearIfCurrent(opened: BluetoothLink?, session: ObdEngine?) {
        if (opened != null && link === opened) link = null
        if (session != null && engine === session) engine = null
    }

    private fun describe(error: Throwable): String = when (error) {
        is SecurityException -> "Permissão de Bluetooth não concedida."
        is ObdException -> error.message
        is ElmTimeoutException -> "O adaptador parou de responder."
        is IOException -> error.message ?: "Falha na comunicação Bluetooth."
        else -> error.message
    } ?: "Erro desconhecido."
}
