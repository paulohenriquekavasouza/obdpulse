package br.com.obdpulse.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import br.com.obdpulse.ObdManager
import br.com.obdpulse.R
import br.com.obdpulse.obd.Labels
import br.com.obdpulse.obd.ObdState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GraphActivity : Activity() {

    private val scope = MainScope()
    private var job: Job? = null
    private lateinit var spinner: Spinner
    private lateinit var current: TextView
    private lateinit var graph: GraphView
    private var keys: List<String> = emptyList()
    private var selected: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)
        spinner = findViewById(R.id.graph_metric)
        current = findViewById(R.id.graph_current)
        graph = findViewById(R.id.graph)
        current.text = "—"
        buildSpinner(ObdManager.state.value)
    }

    override fun onStart() {
        super.onStart()
        job = scope.launch { ObdManager.state.collect(::render) }
    }

    override fun onStop() {
        job?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildSpinner(state: ObdState) {
        val newKeys = state.values.map { it.key }
        if (newKeys == keys) return
        keys = newKeys
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, keys.map { Labels.short(it) },
        )
        val target = keys.indexOf(selected).takeIf { it >= 0 } ?: 0
        spinner.setSelection(target)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = keys.getOrNull(position)
                if (key != selected) {
                    selected = key
                    graph.clear()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        if (selected == null) selected = keys.firstOrNull()
    }

    private fun render(state: ObdState) {
        buildSpinner(state)
        val key = selected ?: return
        val live = state.values.firstOrNull { it.key == key } ?: return
        current.text = "${Labels.short(key)}: ${live.text} ${live.unit}".trim()
        graph.addSample(live.value)
    }
}
