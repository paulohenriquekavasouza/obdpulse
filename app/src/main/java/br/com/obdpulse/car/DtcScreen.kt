package br.com.obdpulse.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.obdpulse.ObdManager
import br.com.obdpulse.R
import br.com.obdpulse.obd.DtcCode
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DtcScreen(carContext: CarContext) : Screen(carContext) {

    private var state: ObdState = ObdManager.state.value
    private val limit = carContext.listLimit()
    private val initialReads = state.dtcReadCount
    private var done = !ObdManager.requestDtcs()

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ObdManager.state
                    .map { Triple(it.status, it.dtcReadCount, it.dtcs) }
                    .distinctUntilChanged()
                    .collect {
                        val current = ObdManager.state.value
                        if (current.dtcReadCount != initialReads || current.status != ObdStatus.CONNECTED) done = true
                        state = current
                        invalidate()
                    }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.dtc_title))
            .setHeaderAction(Action.BACK)
        if (!done) return builder.setLoading(true).build()

        val report = state.dtcs
        val rows = mutableListOf<Row>()
        if (report == null) {
            rows += message(state.message ?: carContext.getString(R.string.dtc_not_connected))
        } else {
            fun add(codes: List<DtcCode>, kind: Int) = codes.forEach {
                rows += Row.Builder()
                    .setTitle(it.code)
                    .addText(carContext.getString(R.string.dtc_row, carContext.getString(kind), it.ecu))
                    .build()
            }
            add(report.stored, R.string.dtc_stored_one)
            add(report.pending, R.string.dtc_pending_one)
            add(report.permanent, R.string.dtc_permanent_one)
            if (rows.isEmpty()) rows += message(carContext.getString(R.string.dtc_none))
        }
        val items = ItemList.Builder()
        rows.take(limit.coerceAtLeast(1)).forEach(items::addItem)
        return builder.setSingleList(items.build()).build()
    }

    private fun message(text: String) = Row.Builder().setTitle(text).build()
}
