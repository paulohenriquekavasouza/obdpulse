package br.com.obdpulse.obd

object Economy {

    fun score(avgKmPerLiter: Double?, hardAccels: Int, distanceKm: Double): Int? {
        val avg = avgKmPerLiter ?: return null
        val base = ((avg - LOW) / (HIGH - LOW) * 100.0).coerceIn(0.0, 100.0)
        val penalty = if (distanceKm >= 0.5) {
            (hardAccels / distanceKm * PENALTY_PER_ACCEL_PER_KM).coerceAtMost(MAX_PENALTY)
        } else {
            (hardAccels * 2.0).coerceAtMost(MAX_PENALTY)
        }
        return (base - penalty).coerceIn(0.0, 100.0).toInt()
    }

    fun rangeKm(fuelLevelPercent: Double?, tankLiters: Double, avgKmPerLiter: Double?): Double? {
        if (fuelLevelPercent == null || avgKmPerLiter == null || avgKmPerLiter <= 0.0) return null
        val remaining = tankLiters * (fuelLevelPercent / 100.0)
        return remaining * avgKmPerLiter
    }

    private const val LOW = 4.0
    private const val HIGH = 14.0
    private const val PENALTY_PER_ACCEL_PER_KM = 3.0
    private const val MAX_PENALTY = 35.0
}
