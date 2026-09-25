package br.com.obdpulse.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import br.com.obdpulse.ObdManager
import br.com.obdpulse.Prefs
import br.com.obdpulse.R
import br.com.obdpulse.obd.DtcCode
import br.com.obdpulse.obd.Labels
import br.com.obdpulse.obd.LiveValue
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.service.ObdService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private class PairedDevice(val name: String, val address: String)
    private class ValueRow(val name: TextView, val value: TextView)

    private val scope = MainScope()
    private var stateJob: Job? = null
    private var pairedDevices: List<PairedDevice> = emptyList()
    private val rows = LinkedHashMap<String, ValueRow>()
    private var renderedKeys: List<String> = emptyList()
    private var favorites: List<String> = emptyList()

    private lateinit var status: TextView
    private lateinit var devices: Spinner
    private lateinit var connect: Button
    private lateinit var readDtc: Button
    private lateinit var copyDiagnostics: Button
    private lateinit var info: TextView
    private lateinit var dtc: TextView
    private lateinit var valuesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        devices = findViewById(R.id.devices)
        connect = findViewById(R.id.connect)
        readDtc = findViewById(R.id.read_dtc)
        copyDiagnostics = findViewById(R.id.copy_diagnostics)
        info = findViewById(R.id.info)
        dtc = findViewById(R.id.dtc)
        valuesContainer = findViewById(R.id.values)
        favorites = Prefs.favorites(this)

        connect.setOnClickListener { toggleConnection() }
        readDtc.setOnClickListener { ObdManager.requestDtcs() }
        copyDiagnostics.setOnClickListener { copyDiagnostics() }
        findViewById<Button>(R.id.refresh).setOnClickListener { loadDevices() }
        ensurePermissions()
    }

    override fun onStart() {
        super.onStart()
        stateJob = scope.launch { ObdManager.state.collect(::render) }
    }

    override fun onStop() {
        stateJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadDevices()
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensurePermissions() {
        val missing = requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) loadDevices() else requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun hasConnectPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun loadDevices() {
        if (!hasConnectPermission()) {
            status.setText(R.string.permission_needed)
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            status.setText(R.string.bluetooth_missing)
            return
        }
        if (!adapter.isEnabled) {
            status.setText(R.string.bluetooth_off)
            return
        }
        pairedDevices = adapter.bondedDevices.orEmpty()
            .map { PairedDevice(it.name ?: it.address, it.address) }
            .sortedByDescending { device -> OBD_HINTS.any { device.name.contains(it, ignoreCase = true) } }
        devices.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            pairedDevices.map { "${it.name} (${it.address})" },
        )
        val saved = pairedDevices.indexOfFirst { it.address == Prefs.address(this) }
        if (saved >= 0) devices.setSelection(saved)
        if (pairedDevices.isEmpty()) status.setText(R.string.no_paired)
    }

    private fun toggleConnection() {
        if (ObdManager.state.value.isActive) {
            ObdManager.disconnect()
            return
        }
        val device = pairedDevices.getOrNull(devices.selectedItemPosition)
        if (device == null) {
            Toast.makeText(this, R.string.select_device, Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.saveAddress(this, device.address)
        ObdService.start(this, device.address)
    }

    private fun copyDiagnostics() {
        copyDiagnostics.isEnabled = false
        copyDiagnostics.setText(R.string.diagnostics_reading)
        scope.launch {
            try {
                val report = ObdManager.diagnosticsReport()
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), report))
                Toast.makeText(this@MainActivity, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
            } finally {
                copyDiagnostics.isEnabled = true
                copyDiagnostics.setText(R.string.copy_diagnostics)
            }
        }
    }

    private fun render(state: ObdState) {
        status.update(
            when (state.status) {
                ObdStatus.DISCONNECTED -> getString(R.string.status_disconnected)
                ObdStatus.CONNECTING -> getString(R.string.status_connecting)
                ObdStatus.INITIALIZING -> getString(R.string.status_initializing)
                ObdStatus.CONNECTED -> getString(R.string.status_connected)
                ObdStatus.ERROR -> getString(R.string.status_error, state.message.orEmpty())
            },
        )
        connect.update(getString(if (state.isActive) R.string.disconnect else R.string.connect))
        devices.isEnabled = !state.isActive
        readDtc.isEnabled = state.status == ObdStatus.CONNECTED && !state.dtcLoading
        info.update(infoText(state))
        dtc.update(dtcText(state))
        renderValues(state.values)
    }

    private fun TextView.update(value: CharSequence) {
        if (text.toString() != value.toString()) text = value
    }

    private fun infoText(state: ObdState): String = buildList {
        state.adapter?.let { add(getString(R.string.info_adapter, it)) }
        state.protocol?.let { add(getString(R.string.info_protocol, it)) }
        state.vin?.let { add(getString(R.string.info_vin, it)) }
        for (ecu in state.ecus) {
            val label = ecu.label ?: getString(R.string.ecu_unknown)
            add(resources.getQuantityString(R.plurals.info_ecu, ecu.supportedPids, ecu.header, label, ecu.supportedPids))
        }
        val undecoded = state.ecus.sumOf { it.undecodedPids.size }
        if (undecoded > 0) add(resources.getQuantityString(R.plurals.info_undecoded, undecoded, undecoded))
        state.milOn?.let {
            val count = state.dtcCount ?: 0
            add(resources.getQuantityString(if (it) R.plurals.info_mil_on else R.plurals.info_mil_off, count, count))
        }
        if (state.status == ObdStatus.CONNECTED) state.message?.let { add(it) }
    }.joinToString("\n")

    private fun dtcText(state: ObdState): String {
        if (state.dtcLoading) return getString(R.string.dtc_reading)
        val report = state.dtcs ?: return ""
        fun line(label: Int, codes: List<DtcCode>) = getString(label) + ": " +
            if (codes.isEmpty()) getString(R.string.none) else codes.joinToString { "${it.code} (${it.ecu})" }
        return listOf(
            line(R.string.dtc_stored, report.stored),
            line(R.string.dtc_pending, report.pending),
            line(R.string.dtc_permanent, report.permanent),
        ).joinToString("\n")
    }

    private fun renderValues(values: List<LiveValue>) {
        val keys = values.map { it.key }
        if (keys != renderedKeys) {
            valuesContainer.removeAllViews()
            rows.clear()
            for (value in values) {
                val row = layoutInflater.inflate(R.layout.item_value, valuesContainer, false)
                row.setOnClickListener {
                    favorites = Prefs.toggleFavorite(this, value.key)
                    refreshLabels()
                }
                valuesContainer.addView(row)
                rows[value.key] = ValueRow(row.findViewById(R.id.name), row.findViewById(R.id.value))
            }
            renderedKeys = keys
            refreshLabels()
        }
        for (value in values) {
            rows[value.key]?.value?.update(getString(R.string.value_with_unit, value.text, value.unit).trim())
        }
    }

    private fun refreshLabels() {
        for ((key, row) in rows) {
            val label = Labels.name(key)
            row.name.text = if (key in favorites) "★ $label" else label
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private val OBD_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "KONNWEI")
    }
}
