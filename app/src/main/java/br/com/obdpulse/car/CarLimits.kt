package br.com.obdpulse.car

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.versioning.CarAppApiLevels

internal fun CarContext.listLimit(): Int =
    if (carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
        getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else {
        DEFAULT_LIST_LIMIT
    }

private const val DEFAULT_LIST_LIMIT = 6
