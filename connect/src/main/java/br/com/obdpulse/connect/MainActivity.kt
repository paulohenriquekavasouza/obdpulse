package br.com.obdpulse.connect

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : Activity() {

    private val scope = MainScope()

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var pin: EditText
    private lateinit var save: Switch
    private lateinit var connect: Button
    private lateinit var status: TextView
    private lateinit var vehicleSpinner: Spinner
    private lateinit var info: TextView
    private lateinit var lock: Button
    private lateinit var unlock: Button
    private lateinit var locate: Button

    private var session: UconnectSession? = null
    private var vehicles: List<UconnectVehicle> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        email = findViewById(R.id.uconnect_email)
        password = findViewById(R.id.uconnect_password)
        pin = findViewById(R.id.uconnect_pin)
        save = findViewById(R.id.uconnect_save)
        connect = findViewById(R.id.uconnect_connect)
        status = findViewById(R.id.uconnect_status)
        vehicleSpinner = findViewById(R.id.uconnect_vehicle)
        info = findViewById(R.id.uconnect_info)
        lock = findViewById(R.id.uconnect_lock)
        unlock = findViewById(R.id.uconnect_unlock)
        locate = findViewById(R.id.uconnect_locate)

        connect.setOnClickListener { doConnect() }
        lock.setOnClickListener { runCommand(UconnectClient.CMD_LOCK, R.string.uconnect_locking) }
        unlock.setOnClickListener { runCommand(UconnectClient.CMD_UNLOCK, R.string.uconnect_unlocking) }
        locate.setOnClickListener { doLocate() }

        if (UconnectStore.isSaved(this)) {
            email.setText(UconnectStore.email(this).orEmpty())
            password.setText(UconnectStore.password(this).orEmpty())
            pin.setText(UconnectStore.pin(this).orEmpty())
            doConnect()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun doConnect() {
        val user = email.text.toString().trim()
        val pass = password.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, R.string.uconnect_need_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        if (save.isChecked) UconnectStore.save(this, user, pass, pin.text.toString())
        setBusy(true)
        status.setText(R.string.uconnect_status_connecting)
        scope.launch {
            try {
                val newSession = UconnectClient.login(user, pass)
                val list = UconnectClient.listVehicles(newSession)
                session = newSession
                vehicles = list
                if (list.isEmpty()) {
                    status.setText(R.string.uconnect_status_no_vehicles)
                } else {
                    populateVehicles(list)
                    status.text = getString(R.string.uconnect_status_connected, list.size)
                }
            } catch (e: Exception) {
                status.text = getString(R.string.uconnect_status_error, e.message.orEmpty())
            } finally {
                setBusy(false)
            }
        }
    }

    private fun populateVehicles(list: List<UconnectVehicle>) {
        vehicleSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, list.map { it.label },
        )
        val savedVin = UconnectStore.vin(this)
        val index = list.indexOfFirst { it.vin == savedVin }.takeIf { it >= 0 } ?: 0
        vehicleSpinner.setSelection(index)
        vehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedVehicle()?.let {
                    UconnectStore.saveVin(this@MainActivity, it.vin)
                    info.text = it.label
                    loadLocation(it)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        setBusy(false)
    }

    private fun selectedVehicle(): UconnectVehicle? = vehicles.getOrNull(vehicleSpinner.selectedItemPosition)

    private fun loadLocation(vehicle: UconnectVehicle) {
        val current = session ?: return
        scope.launch {
            try {
                val location = UconnectClient.location(current, vehicle.vin)
                info.text = locationText(vehicle, location)
            } catch (e: Exception) {
                info.text = getString(R.string.uconnect_status_error, e.message.orEmpty())
            }
        }
    }

    private fun runCommand(name: String, busyLabel: Int) {
        val current = session ?: return
        val vehicle = selectedVehicle() ?: return
        val pinValue = pin.text.toString()
        if (pinValue.isEmpty()) {
            Toast.makeText(this, R.string.uconnect_need_pin, Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true)
        status.setText(busyLabel)
        scope.launch {
            try {
                val pinAuth = UconnectClient.authenticatePin(current, pinValue)
                UconnectClient.command(current, vehicle.vin, name, pinAuth)
                status.setText(R.string.uconnect_command_sent)
            } catch (e: Exception) {
                status.text = getString(R.string.uconnect_status_error, e.message.orEmpty())
            } finally {
                setBusy(false)
            }
        }
    }

    private fun doLocate() {
        val current = session ?: return
        val vehicle = selectedVehicle() ?: return
        val pinValue = pin.text.toString()
        setBusy(true)
        status.setText(R.string.uconnect_locating)
        scope.launch {
            try {
                if (pinValue.isNotEmpty()) {
                    val pinAuth = UconnectClient.authenticatePin(current, pinValue)
                    UconnectClient.command(current, vehicle.vin, UconnectClient.CMD_LOCATE, pinAuth)
                }
                val location = UconnectClient.location(current, vehicle.vin)
                info.text = locationText(vehicle, location)
                status.setText(R.string.uconnect_status_ok)
            } catch (e: Exception) {
                status.text = getString(R.string.uconnect_status_error, e.message.orEmpty())
            } finally {
                setBusy(false)
            }
        }
    }

    private fun locationText(vehicle: UconnectVehicle, location: VehicleLocation?): String {
        if (location == null) return vehicle.label
        return getString(
            R.string.uconnect_location,
            vehicle.label,
            String.format(Locale.US, "%.5f", location.latitude),
            String.format(Locale.US, "%.5f", location.longitude),
        )
    }

    private fun setBusy(busy: Boolean) {
        connect.isEnabled = !busy
        val ready = !busy && session != null
        lock.isEnabled = ready
        unlock.isEnabled = ready
        locate.isEnabled = ready
    }
}
