package br.com.obdpulse.trips

import br.com.obdpulse.obd.Economy

data class TripRecord(
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    val fuelUsedL: Double,
    val maxSpeed: Double?,
    val maxRpm: Double?,
    val maxBoost: Double?,
    val best0100Ms: Long?,
    val hardAccels: Int,
    val pricePerLiter: Double,
) {
    val id: Long get() = startMs

    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    val kmPerLiter: Double? get() = if (fuelUsedL >= MIN_FUEL_L) distanceKm / fuelUsedL else null

    val cost: Double get() = fuelUsedL * pricePerLiter

    val ecoScore: Int? get() = Economy.score(kmPerLiter, hardAccels, distanceKm)

    companion object {
        const val MIN_FUEL_L = 0.01
    }
}

data class TripTotals(
    val count: Int,
    val distanceKm: Double,
    val fuelUsedL: Double,
    val cost: Double,
    val avgKmPerLiter: Double?,
    val avgScore: Int?,
)

object TripCodec {

    private const val SEPARATOR = ";"
    private const val FIELDS = 10

    fun encode(record: TripRecord): String = listOf(
        record.startMs,
        record.endMs,
        record.distanceKm,
        record.fuelUsedL,
        record.maxSpeed ?: "",
        record.maxRpm ?: "",
        record.maxBoost ?: "",
        record.best0100Ms ?: "",
        record.hardAccels,
        record.pricePerLiter,
    ).joinToString(SEPARATOR)

    fun decode(line: String): TripRecord? {
        val parts = line.trim().split(SEPARATOR)
        if (parts.size < FIELDS) return null
        return try {
            TripRecord(
                startMs = parts[0].toLong(),
                endMs = parts[1].toLong(),
                distanceKm = parts[2].toDouble(),
                fuelUsedL = parts[3].toDouble(),
                maxSpeed = parts[4].toDoubleOrNull(),
                maxRpm = parts[5].toDoubleOrNull(),
                maxBoost = parts[6].toDoubleOrNull(),
                best0100Ms = parts[7].toLongOrNull(),
                hardAccels = parts[8].toInt(),
                pricePerLiter = parts[9].toDouble(),
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun encodeAll(records: List<TripRecord>): String = records.joinToString("\n") { encode(it) }

    fun decodeAll(text: String): List<TripRecord> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { decode(it) }
            .sortedBy { it.startMs }
            .toList()
}

object TripHistory {

    const val MAX_TRIPS = 500

    fun upsert(records: List<TripRecord>, record: TripRecord): List<TripRecord> =
        (records.filterNot { it.id == record.id } + record)
            .sortedBy { it.startMs }
            .takeLast(MAX_TRIPS)

    fun remove(records: List<TripRecord>, id: Long): List<TripRecord> = records.filterNot { it.id == id }

    fun totals(records: List<TripRecord>): TripTotals {
        val withFuel = records.filter { it.kmPerLiter != null }
        val fuel = withFuel.sumOf { it.fuelUsedL }
        val scores = records.mapNotNull { it.ecoScore }
        return TripTotals(
            count = records.size,
            distanceKm = records.sumOf { it.distanceKm },
            fuelUsedL = records.sumOf { it.fuelUsedL },
            cost = records.sumOf { it.cost },
            avgKmPerLiter = if (fuel > 0.0) withFuel.sumOf { it.distanceKm } / fuel else null,
            avgScore = if (scores.isEmpty()) null else Math.round(scores.average()).toInt(),
        )
    }
}
