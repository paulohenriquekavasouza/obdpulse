package br.com.obdpulse.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import br.com.obdpulse.ObdManager
import br.com.obdpulse.Prefs
import br.com.obdpulse.R
import br.com.obdpulse.obd.Economy
import br.com.obdpulse.obd.Format
import br.com.obdpulse.obd.ObdState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TripActivity : Activity() {

    private val scope = MainScope()
    private var job: Job? = null
    private lateinit var summary: TextView
    private lateinit var fuelPrice: EditText
    private lateinit var tankLiters: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip)
        summary = findViewById(R.id.trip_summary)
        fuelPrice = findViewById(R.id.fuel_price)
        tankLiters = findViewById(R.id.tank_liters)
        fuelPrice.setText(Format.number(Prefs.fuelPrice(this).toDouble(), 2))
        tankLiters.setText(Format.number(Prefs.tankLiters(this).toDouble(), 0))
        findViewById<Button>(R.id.save_trip).setOnClickListener { save() }
    }

    override fun onStart() {
        super.onStart()
        job = scope.launch { ObdManager.state.collectThrottled(this@TripActivity, ::render) }
    }

    override fun onStop() {
        job?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun save() {
        parse(fuelPrice)?.let { Prefs.saveFuelPrice(this, it.toFloat()) }
        parse(tankLiters)?.let { Prefs.saveTankLiters(this, it.toFloat()) }
        render(ObdManager.state.value)
        Toast.makeText(this, R.string.trip_saved, Toast.LENGTH_SHORT).show()
    }

    private fun parse(field: EditText): Double? =
        field.text.toString().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

    private fun render(state: ObdState) {
        val trip = state.trip
        val price = Prefs.fuelPrice(this).toDouble()
        val tank = Prefs.tankLiters(this).toDouble()
        val fuelLevel = state.values.firstOrNull { it.key == "2F" }?.value
        val cost = trip.fuelUsedL * price
        val range = Economy.rangeKm(fuelLevel, tank, trip.avgKmPerLiter)
        val eco = Economy.score(trip.avgKmPerLiter, trip.hardAccels, trip.distanceKm)

        summary.text = listOf(
            line(R.string.trip_distance, "${Format.number(trip.distanceKm, 1)} km"),
            line(R.string.trip_fuel_used, "${Format.number(trip.fuelUsedL, 2)} L"),
            line(R.string.trip_cost, "R$ ${Format.number(cost, 2)}"),
            line(R.string.trip_avg, trip.avgKmPerLiter?.let { "${Format.number(it, 1)} km/L" }),
            line(R.string.trip_range, range?.let { "${Format.number(it, 0)} km" }),
            line(R.string.trip_eco, eco?.let { "$it / 100" }),
        ).joinToString("\n")
    }

    private fun line(label: Int, value: String?): String = "${getString(label)}: ${value ?: EMPTY}"

    private companion object {
        const val EMPTY = "—"
    }
}
