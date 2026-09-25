package br.com.obdpulse.car

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ListTemplate
import androidx.car.app.versioning.CarAppApiLevels

internal fun CarContext.listLimit(): Int =
    if (carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
        getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else {
        DEFAULT_LIST_LIMIT
    }

internal fun ListTemplate.Builder.header(
    carContext: CarContext,
    title: CharSequence,
    startAction: Action,
    endAction: Action? = null,
): ListTemplate.Builder =
    if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_7) {
        val header = Header.Builder().setTitle(title).setStartHeaderAction(startAction)
        if (endAction != null) header.addEndHeaderAction(endAction)
        setHeader(header.build())
    } else {
        legacyHeader(title, startAction, endAction)
    }

@Suppress("DEPRECATION")
private fun ListTemplate.Builder.legacyHeader(
    title: CharSequence,
    startAction: Action,
    endAction: Action?,
): ListTemplate.Builder {
    setTitle(title)
    setHeaderAction(startAction)
    if (endAction != null) setActionStrip(ActionStrip.Builder().addAction(endAction).build())
    return this
}

private const val DEFAULT_LIST_LIMIT = 6
