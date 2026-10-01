package br.com.obdpulse.ui

import android.content.Context
import br.com.obdpulse.Prefs
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

suspend fun Flow<ObdState>.collectThrottled(context: Context, action: (ObdState) -> Unit) {
    var lastKey: Triple<ObdStatus, Boolean, Int>? = null
    collect { state ->
        val key = Triple(state.status, state.dtcLoading, state.dtcReadCount)
        val changed = key != lastKey
        lastKey = key
        action(state)
        if (!changed) delay(Prefs.refreshMs(context))
    }
}
