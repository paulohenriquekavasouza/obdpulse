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
    private lateinit var logout: Button
    private lateinit var map: WebView

    private var vehicles: List<UconnectVehicle> = emptyList()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionHolder.session == null) {
            backToLogin()
            return
        }
        setContentView(R.layout.activity_actions)
        vehicleSpinner = findViewById(R.id.uconnect_vehicle)
        info = findViewById(R.id.uconnect_info)
        lock = findViewById(R.id.uconnect_lock)
        unlock = findViewById(R.id.uconnect_unlock)
        locate = findViewById(R.id.uconnect_locate)
        logout = findViewById(R.id.uconnect_logout)
        map = findViewById(R.id.map)
        map.settings.javaScriptEnabled = true

        lock.setOnClickListener { runCommand(UconnectClient.CMD_LOCK, R.string.uconnect_locking) }
        unlock.setOnClickListener { runCommand(UconnectClient.CMD_UNLOCK, R.string.uconnect_unlocking) }
        locate.setOnClickListener { doLocate() }
        logout.setOnClickListener {
            UconnectStore.clear(this)
            SessionHolder.clear()
            backToLogin()
        }

        setCommandsEnabled(false)
        loadVehicles()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadVehicles() {
        val session = SessionHolder.session ?: return backToLogin()
        info.setText(R.string.actions_loading)
        scope.launch {
            try {
                val list = UconnectClient.listVehicles(session)
                vehicles = list
                if (list.isEmpty()) {
                    info.setText(R.string.uconnect_status_no_vehicles)
                    return@launch
                }
                vehicleSpinner.adapter = ArrayAdapter(
                    this@ActionsActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    list.map { it.label },
                )
                val savedVin = UconnectStore.vin(this@ActionsActivity)
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
                setCommandsEnabled(true)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun selectedVehicle(): UconnectVehicle? = vehicles.getOrNull(vehicleSpinner.selectedItemPosition)

    private fun loadLocation(vehicle: UconnectVehicle) {
        val session = SessionHolder.session ?: return
        scope.launch {
            try {
                val location = UconnectClient.location(session, vehicle.vin)
                showLocation(vehicle, location)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun runCommand(name: String, busyLabel: Int) {
        val session = SessionHolder.session ?: return backToLogin()
        val vehicle = selectedVehicle() ?: return
        val pinValue = SessionHolder.pin.orEmpty()
        if (pinValue.isEmpty()) {
            Toast.makeText(this, R.string.uconnect_need_pin, Toast.LENGTH_SHORT).show()
            return
        }
        setCommandsEnabled(false)
        info.setText(busyLabel)
        scope.launch {
            try {
                val pinAuth = UconnectClient.authenticatePin(session, pinValue)
                UconnectClient.command(session, vehicle.vin, name, pinAuth)
                info.setText(R.string.uconnect_command_sent)
            } catch (e: Exception) {
                onError(e)
            } finally {
                setCommandsEnabled(true)
            }
        }
    }

    private fun doLocate() {
        val session = SessionHolder.session ?: return backToLogin()
        val vehicle = selectedVehicle() ?: return
        val pinValue = SessionHolder.pin.orEmpty()
        setCommandsEnabled(false)
        info.setText(R.string.uconnect_locating)
        scope.launch {
            try {
                if (pinValue.isNotEmpty()) {
                    val pinAuth = UconnectClient.authenticatePin(session, pinValue)
                    UconnectClient.command(session, vehicle.vin, UconnectClient.CMD_LOCATE, pinAuth)
                }
                showLocation(vehicle, UconnectClient.location(session, vehicle.vin))
            } catch (e: Exception) {
                onError(e)
            } finally {
                setCommandsEnabled(true)
            }
        }
    }

    private fun showLocation(vehicle: UconnectVehicle, location: VehicleLocation?) {
        if (location == null) {
            info.text = vehicle.label
            return
        }
        info.text = getString(
            R.string.uconnect_location,
            vehicle.label,
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

    private fun onError(e: Exception) {
        val message = e.message.orEmpty()
        if (looksExpired(message)) {
            SessionHolder.session = null
            Toast.makeText(this, R.string.uconnect_session_expired, Toast.LENGTH_SHORT).show()
            backToLogin()
        } else {
            info.text = getString(R.string.uconnect_status_error, message)
        }
    }

    private fun looksExpired(message: String): Boolean {
        val m = message.lowercase()
        return "403" in m || "forbidden" in m || "expired" in m || "security token" in m || "credential" in m
    }

    private fun backToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setCommandsEnabled(enabled: Boolean) {
        lock.isEnabled = enabled
        unlock.isEnabled = enabled
        locate.isEnabled = enabled
    }
}
