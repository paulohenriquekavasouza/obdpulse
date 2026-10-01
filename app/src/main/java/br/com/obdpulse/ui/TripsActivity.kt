package br.com.obdpulse.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import br.com.obdpulse.R
import br.com.obdpulse.trips.FileTripStore
import br.com.obdpulse.trips.TripFormat
import br.com.obdpulse.trips.TripHistory
import br.com.obdpulse.trips.TripRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripsActivity : Activity() {

    private val scope = MainScope()
    private lateinit var store: FileTripStore
    private lateinit var summary: TextView
    private lateinit var chart: TrendChartView
    private lateinit var list: LinearLayout
    private lateinit var clear: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trips)
        store = FileTripStore(this)
        summary = findViewById(R.id.trips_summary)
        chart = findViewById(R.id.trips_chart)
        list = findViewById(R.id.trips_list)
        clear = findViewById(R.id.trips_clear)
        clear.setOnClickListener { confirmClear() }
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun reload() {
        scope.launch {
            val trips = withContext(Dispatchers.IO) { store.all() }
            render(trips)
        }
    }

    private fun render(trips: List<TripRecord>) {
        if (trips.isEmpty()) {
            summary.setText(R.string.trips_empty)
        } else {
            summary.text = TripFormat.summary(TripHistory.totals(trips))
        }
        renderChart(trips)
        renderList(trips)
        clear.isEnabled = trips.isNotEmpty()
    }

    private fun renderChart(trips: List<TripRecord>) {
        val points = trips.filter { it.kmPerLiter != null }.takeLast(CHART_TRIPS)
        chart.setData(
            values = points.map { it.kmPerLiter ?: 0.0 },
            firstLabel = points.firstOrNull()?.let { TripFormat.shortDate(it.startMs) }.orEmpty(),
            lastLabel = points.lastOrNull()?.let { TripFormat.shortDate(it.startMs) }.orEmpty(),
            emptyText = getString(R.string.trips_chart_empty),
        )
    }

    private fun renderList(trips: List<TripRecord>) {
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (trip in trips.asReversed().take(LIST_TRIPS)) {
            val row = inflater.inflate(R.layout.item_trip, list, false)
            row.findViewById<TextView>(R.id.trip_title).text = TripFormat.title(trip)
            row.findViewById<TextView>(R.id.trip_details).text = TripFormat.summaryLine(trip)
            row.setOnClickListener { showDetail(trip) }
            list.addView(row)
        }
    }

    private fun showDetail(trip: TripRecord) {
        AlertDialog.Builder(this)
            .setTitle(TripFormat.title(trip))
            .setMessage(TripFormat.detail(trip))
            .setPositiveButton(R.string.trips_close, null)
            .setNegativeButton(R.string.trips_delete) { _, _ -> deleteTrip(trip) }
            .show()
    }

    private fun deleteTrip(trip: TripRecord) {
        scope.launch {
            withContext(Dispatchers.IO) { store.remove(trip.id) }
            reload()
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.trips_clear)
            .setMessage(R.string.trips_clear_confirm)
            .setPositiveButton(R.string.trips_clear) { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.clear() }
                    reload()
                }
            }
            .setNegativeButton(R.string.trips_close, null)
            .show()
    }

    private companion object {
        const val CHART_TRIPS = 60
        const val LIST_TRIPS = 100
    }
}
