package br.com.obdpulse.obd

class TripTracker {

    private var maxBoost: Double? = null
    private var maxRpm: Double? = null
    private var maxSpeed: Double? = null
    private var maxCoolant: Double? = null
    private var maxIntake: Double? = null

    private var sumSpeed = 0.0
    private var sumRate = 0.0
    private var distanceKm = 0.0
    private var fuelUsedL = 0.0
    private var hardAccels = 0
    private var lastIntegrateMs: Long? = null

    private var prevSpeed: Double? = null
    private var prevTimeMs = 0L
    private var launchTimeMs: Long? = null
    private var best: Long? = null
    private var last: Long? = null
    private var lastSub10: Long? = null

    fun onSample(nowMs: Long, values: Map<String, LiveValue>): TripStats {
        val boost = values[Keys.BOOST]?.value
        val rpm = values["0C"]?.value
        val speed = values["0D"]?.value
        val coolant = values["05"]?.value
        val intake = values["77"]?.value
        val rate = values["5E"]?.value

        maxBoost = keepMax(maxBoost, boost)
        maxRpm = keepMax(maxRpm, rpm)
        maxSpeed = keepMax(maxSpeed, speed)
        maxCoolant = keepMax(maxCoolant, coolant)
        maxIntake = keepMax(maxIntake, intake)

        if (speed != null && rate != null) {
            sumSpeed += speed
            sumRate += rate
        }

        val previousMs = lastIntegrateMs
        lastIntegrateMs = nowMs
        if (previousMs != null) {
            val dtHours = ((nowMs - previousMs).coerceIn(0, MAX_DT_MS)) / 3_600_000.0
            if (speed != null) distanceKm += speed * dtHours
            if (rate != null) fuelUsedL += rate * dtHours
        }

        if (speed != null) updateLaunch(nowMs, speed)

        return stats()
    }

    fun stats(): TripStats = TripStats(
        maxBoost = maxBoost,
        maxRpm = maxRpm,
        maxSpeed = maxSpeed,
        maxCoolant = maxCoolant,
        maxIntake = maxIntake,
        avgKmPerLiter = if (sumRate > 0.0) sumSpeed / sumRate else null,
        bestZeroTo100Ms = best,
        lastZeroTo100Ms = last,
        lastSub10Ms = lastSub10,
        distanceKm = distanceKm,
        fuelUsedL = fuelUsedL,
        hardAccels = hardAccels,
    )

    private fun updateLaunch(nowMs: Long, speed: Double) {
        val prev = prevSpeed
        val prevTime = prevTimeMs
        prevSpeed = speed
        prevTimeMs = nowMs

        if (prev != null && nowMs > prevTime) {
            val accel = (speed - prev) / ((nowMs - prevTime) / 1000.0)
            if (accel >= HARD_ACCEL) hardAccels++
        }

        if (speed <= STANDSTILL) {
            launchTimeMs = null
            return
        }
        if (prev == null) return

        if (prev <= STANDSTILL && launchTimeMs == null) {
            launchTimeMs = cross(prevTime, prev, nowMs, speed, STANDSTILL)
        }
        val start = launchTimeMs
        if (start != null && prev < TARGET && speed >= TARGET) {
            val reached = cross(prevTime, prev, nowMs, speed, TARGET)
            val elapsed = reached - start
            if (elapsed in 1_000..60_000) {
                last = elapsed
                best = best?.let { minOf(it, elapsed) } ?: elapsed
                if (elapsed < 10_000) lastSub10 = elapsed
            }
            launchTimeMs = null
        }
    }

    private fun keepMax(current: Double?, value: Double?): Double? = when {
        value == null -> current
        current == null -> value
        else -> maxOf(current, value)
    }

    private fun cross(t0: Long, v0: Double, t1: Long, v1: Double, target: Double): Long {
        if (v1 == v0) return t1
        val fraction = (target - v0) / (v1 - v0)
        return t0 + ((t1 - t0) * fraction).toLong()
    }

    private companion object {
        const val STANDSTILL = 1.0
        const val TARGET = 100.0
        const val MAX_DT_MS = 3_000L
        const val HARD_ACCEL = 9.0
    }
}
