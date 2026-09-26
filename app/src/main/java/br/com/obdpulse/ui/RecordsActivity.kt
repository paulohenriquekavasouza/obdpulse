package br.com.obdpulse.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import br.com.obdpulse.ObdManager
import br.com.obdpulse.Prefs
import br.com.obdpulse.R
import br.com.obdpulse.obd.Format
import br.com.obdpulse.obd.TripStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RecordsActivity : Activity() {

    private val scope = MainScope()
    private var job: Job? = null
    private lateinit var sessionView: TextView
    private lateinit var allTimeView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)
        sessionView = findViewById(R.id.session_records)
        allTimeView = findViewById(R.id.alltime_records)
        findViewById<Button>(R.id.reset_records).setOnClickListener {
            Prefs.resetRecords(this)
            renderAllTime()
        }
        renderAllTime()
    }

    override fun onStart() {
        super.onStart()
        job = scope.launch {
            ObdManager.state.collect {
                renderSession(it.trip)
                renderAllTime()
            }
        }
    }

    override fun onStop() {
        job?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderSession(trip: TripStats) {
        sessionView.text = listOf(
            line(R.string.rec_0100, trip.bestZeroTo100Ms?.let { "${Format.number(it / 1000.0, 1)} s" }),
            line(R.string.rec_vmax, trip.maxSpeed?.let { "${Format.number(it, 0)} km/h" }),
            line(R.string.rec_rpm, trip.maxRpm?.let { "${Format.number(it, 0)} rpm" }),
            line(R.string.rec_boost, trip.maxBoost?.let { "${Format.number(it, 2)} bar" }),
        ).joinToString("\n")
    }

    private fun renderAllTime() {
        val rec = Prefs.records(this)
        allTimeView.text = listOf(
            line(R.string.rec_0100, rec.best0100Ms?.let { "${Format.number(it / 1000.0, 1)} s" }),
            line(R.string.rec_vmax, rec.maxSpeed?.let { "${Format.number(it, 0)} km/h" }),
            line(R.string.rec_rpm, rec.maxRpm?.let { "${Format.number(it, 0)} rpm" }),
            line(R.string.rec_boost, rec.maxBoost?.let { "${Format.number(it, 2)} bar" }),
        ).joinToString("\n")
    }

    private fun line(label: Int, value: String?): String = "${getString(label)}: ${value ?: EMPTY}"

    private companion object {
        const val EMPTY = "—"
    }
}
