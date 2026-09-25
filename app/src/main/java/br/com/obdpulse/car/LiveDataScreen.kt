package br.com.obdpulse.car

import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.obdpulse.ObdManager
import br.com.obdpulse.Prefs
import br.com.obdpulse.R
import br.com.obdpulse.obd.Labels
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.service.ObdService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveDataScreen(carContext: CarContext) : Screen(carContext) {

    private var state: ObdState = ObdManager.state.value
    private val limit = carContext.listLimit()
    private var lastRefresh = 0L
    private var refreshPending = false

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ObdManager.state.collect {
                    state = it
                    scheduleRefresh()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val current = state
        val byKey = current.values.associateBy { it.key }
        val favorites = Prefs.favorites(carContext).take((limit - 1).coerceAtLeast(0))
        val items = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_status))
                    .addText(statusLine(current))
                    .setOnClickListener { toggleConnection() }
                    .build(),
            )
        for (key in favorites) {
            val value = byKey[key]
            items.addItem(
                Row.Builder()
                    .setTitle(Labels.name(key))
                    .addText(value?.let { carContext.getString(R.string.value_with_unit, it.text, it.unit).trim() } ?: EMPTY_VALUE)
                    .build(),
            )
        }
        val dtcAction = Action.Builder()
            .setTitle(carContext.getString(R.string.car_dtc))
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_dtc))
                    .setTint(CarColor.DEFAULT)
                    .build(),
            )
            .setOnClickListener { screenManager.push(DtcScreen(carContext)) }
            .build()
        return ListTemplate.Builder()
            .header(carContext, carContext.getString(R.string.app_name), Action.APP_ICON, dtcAction)
            .setSingleList(items.build())
            .build()
    }

    private fun statusLine(current: ObdState): String {
        val base = when (current.status) {
            ObdStatus.DISCONNECTED -> carContext.getString(R.string.car_disconnected)
            ObdStatus.CONNECTING -> carContext.getString(R.string.car_connecting)
            ObdStatus.INITIALIZING -> carContext.getString(R.string.car_initializing)
            ObdStatus.CONNECTED -> carContext.getString(R.string.car_connected)
            ObdStatus.ERROR -> carContext.getString(R.string.car_error, current.message.orEmpty())
        }
        return if (current.status == ObdStatus.CONNECTED && current.milOn == true) {
            carContext.getString(R.string.car_mil, current.dtcCount ?: 0) + " · " + base
        } else {
            base
        }
    }

    private fun toggleConnection() {
        if (ObdManager.state.value.isActive) {
            ObdManager.disconnect()
            return
        }
        val address = Prefs.address(carContext)
        if (address == null) {
            CarToast.makeText(carContext, R.string.car_select_device, CarToast.LENGTH_LONG).show()
            return
        }
        ObdService.start(carContext, address)
    }

    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        val wait = (lastRefresh + REFRESH_INTERVAL - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        lifecycleScope.launch {
            delay(wait)
            refreshPending = false
            lastRefresh = SystemClock.elapsedRealtime()
            invalidate()
        }
    }

    private companion object {
        const val REFRESH_INTERVAL = 1_000L
        const val EMPTY_VALUE = "—"
    }
}
