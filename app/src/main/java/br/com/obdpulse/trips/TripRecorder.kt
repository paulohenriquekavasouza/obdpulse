package br.com.obdpulse.trips

import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.obd.TripStats

interface TripStorage {
    fun upsert(record: TripRecord)
}

class TripRecorder(
    private val storage: TripStorage,
    private val minDistanceKm: Double = MIN_DISTANCE_KM,
    private val checkpointMs: Long = CHECKPOINT_MS,
) {
    private var startMs: Long? = null
    private var lastCheckpointMs = 0L
    private var last: TripStats? = null
    private var pricePerLiter = 0.0

    fun onState(state: ObdState, nowMs: Long, price: Double) {
        if (state.status != ObdStatus.CONNECTED) return
        if (startMs == null) {
            startMs = nowMs
            lastCheckpointMs = nowMs
        }
        last = state.trip
        pricePerLiter = price
        if (nowMs - lastCheckpointMs >= checkpointMs) {
            lastCheckpointMs = nowMs
            save(nowMs)
        }
    }

    fun finish(nowMs: Long) {
        if (startMs == null) return
        save(nowMs)
        startMs = null
        last = null
    }

    private fun save(nowMs: Long) {
        val start = startMs ?: return
        val stats = last ?: return
        if (stats.distanceKm < minDistanceKm) return
        storage.upsert(
            TripRecord(
                startMs = start,
                endMs = nowMs,
                distanceKm = stats.distanceKm,
                fuelUsedL = stats.fuelUsedL,
                maxSpeed = stats.maxSpeed,
                maxRpm = stats.maxRpm,
                maxBoost = stats.maxBoost,
                best0100Ms = stats.bestZeroTo100Ms,
                hardAccels = stats.hardAccels,
                pricePerLiter = pricePerLiter,
            ),
        )
    }

    companion object {
        const val MIN_DISTANCE_KM = 0.3
        const val CHECKPOINT_MS = 30_000L
    }
}
