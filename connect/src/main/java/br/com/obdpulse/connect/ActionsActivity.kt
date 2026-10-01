package br.com.obdpulse.connect

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class ActionsActivity : Activity() {

    private val scope = MainScope()

    private lateinit var vehicleSpinner: Spinner
    private lateinit var info: TextView
    private lateinit var lock: Button
    private lateinit var unlock: Button
    private lateinit var locate: Button
    private lateinit var map: WebView

    private var vehicles: List<UconnectVehicle> = emptyList()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionHolder.session == null) {
            AuthFlow.relogin(this, SessionHolder.pending)
            return
        }
        setContentView(R.layout.activity_actions)
        vehicleSpinner = findViewById(R.id.uconnect_vehicle)
        info = findViewById(R.id.uconnect_info)
        lock = findViewById(R.id.uconnect_lock)
        unlock = findViewById(R.id.uconnect_unlock)
        locate = findViewById(R.id.uconnect_locate)
        map = findViewById(R.id.map)
        map.settings.javaScriptEnabled = true

        lock.setOnClickListener { selectedVehicle()?.let { performCommand(UconnectClient.CMD_LOCK, it.vin) } }
        unlock.setOnClickListener { selectedVehicle()?.let { performCommand(UconnectClient.CMD_UNLOCK, it.vin) } }
        locate.setOnClickListener { selectedVehicle()?.let { performLocate(it.vin) } }
        findViewById<Button>(R.id.actions_status).setOnClickListener {
            startActivity(Intent(this, StatusActivity::class.java))
        }
        findViewById<Button>(R.id.actions_json).setOnClickListener {
            startActivity(Intent(this, JsonActivity::class.java))
        }
        findViewById<Button>(R.id.uconnect_logout).setOnClickListener { logout() }

        setCommandsEnabled(false)
        val pending = SessionHolder.pending
        SessionHolder.pending = null
        loadVehicles(pending)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadVehicles(pending: PendingAction?) {
        val session = SessionHolder.session ?: return
        info.setText(R.string.actions_loading)
        scope.launch {
            try {
                val list = UconnectClient.listVehicles(session)
                vehicles = list
                if (list.isEmpty()) {
                    info.setText(R.string.uconnect_status_no_vehicles)
                    return@launch
                }
                populateVehicles(list)
                setCommandsEnabled(true)
                pending?.let { resume(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, pending)
            }
        }
    }

    private fun populateVehicles(list: List<UconnectVehicle>) {
        vehicleSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, list.map { it.label },
        )
        val savedVin = UconnectStore.vin(this)
        vehicleSpinner.setSelection(list.indexOfFirst { it.vin == savedVin }.takeIf { it >= 0 } ?: 0)
        vehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedVehicle()?.let {
                    UconnectStore.saveVin(this@ActionsActivity, it.vin)
                    info.text = it.label
                    loadLocation(it)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun selectedVehicle(): UconnectVehicle? = vehicles.getOrNull(vehicleSpinner.selectedItemPosition)

    private fun resume(pending: PendingAction) {
        val vin = pending.vin ?: selectedVehicle()?.vin ?: return
        when (pending.type) {
            PendingAction.Type.COMMAND -> pending.command?.let { performCommand(it, vin) }
            PendingAction.Type.LOCATE -> performLocate(vin)
        }
    }

    private fun loadLocation(vehicle: UconnectVehicle) {
        val session = SessionHolder.session ?: return
        scope.launch {
            try {
                showLocation(vehicle.label, UconnectClient.location(session, vehicle.vin))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, null)
            }
        }
    }

    private fun performCommand(name: String, vin: String) {
        val pending = PendingAction(PendingAction.Type.COMMAND, vin, name)
        val session = SessionHolder.session
        if (session == null) {
            AuthFlow.relogin(this, pending)
            return
        }
        val pinValue = SessionHolder.pin.orEmpty()
        if (pinValue.isEmpty()) {
            Toast.makeText(this, R.string.uconnect_need_pin, Toast.LENGTH_SHORT).show()
            return
        }
        setCommandsEnabled(false)
        info.setText(if (name == UconnectClient.CMD_LOCK) R.string.uconnect_locking else R.string.uconnect_unlocking)
        scope.launch {
            try {
                val pinAuth = UconnectClient.authenticatePin(session, pinValue)
                UconnectClient.command(session, vin, name, pinAuth)
                info.setText(R.string.uconnect_command_sent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, pending)
            } finally {
                setCommandsEnabled(true)
            }
        }
    }

    private fun performLocate(vin: String) {
        val pending = PendingAction(PendingAction.Type.LOCATE, vin, null)
        val session = SessionHolder.session
        if (session == null) {
            AuthFlow.relogin(this, pending)
            return
        }
        val pinValue = SessionHolder.pin.orEmpty()
        setCommandsEnabled(false)
        info.setText(R.string.uconnect_locating)
        scope.launch {
            try {
                if (pinValue.isNotEmpty()) {
                    val pinAuth = UconnectClient.authenticatePin(session, pinValue)
                    UconnectClient.command(session, vin, UconnectClient.CMD_LOCATE, pinAuth)
                }
                val label = vehicles.firstOrNull { it.vin == vin }?.label ?: vin
                showLocation(label, UconnectClient.location(session, vin))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, pending)
            } finally {
                setCommandsEnabled(true)
            }
        }
    }

    private fun showLocation(label: String, location: VehicleLocation?) {
        if (location == null) {
            info.text = label
            return
        }
        info.text = getString(
            R.string.uconnect_location,
            label,
            String.format(Locale.US, "%.5f", location.latitude),
            String.format(Locale.US, "%.5f", location.longitude),
        )
        map.loadDataWithBaseURL("https://unpkg.com", mapHtml(location.latitude, location.longitude), "text/html", "utf-8", null)
    }

    private fun mapHtml(lat: Double, lon: Double): String {
        val la = String.format(Locale.US, "%.6f", lat)
        val lo = String.format(Locale.US, "%.6f", lon)
        return """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>html,body,#map{height:100%;margin:0;background:#0A0C11}</style>
            </head><body><div id="map"></div><script>
            var m=L.map('map').setView([$la,$lo],16);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
              {maxZoom:19,attribution:'© OpenStreetMap'}).addTo(m);
            L.marker([$la,$lo]).addTo(m);
            </script></body></html>
        """.trimIndent()
    }

    private fun handleError(error: Exception, pending: PendingAction?) {
        if (AuthFlow.handle(this, error, pending)) return
        info.text = getString(R.string.uconnect_status_error, error.message.orEmpty())
    }

    private fun logout() {
        UconnectStore.clear(this)
        SessionHolder.clear()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setCommandsEnabled(enabled: Boolean) {
        lock.isEnabled = enabled
        unlock.isEnabled = enabled
        locate.isEnabled = enabled
    }
}
