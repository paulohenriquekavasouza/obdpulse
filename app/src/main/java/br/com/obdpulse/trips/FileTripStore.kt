package br.com.obdpulse.trips

import android.content.Context
import java.io.File

class FileTripStore(context: Context) : TripStorage {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun all(): List<TripRecord> = synchronized(LOCK) { read() }

    override fun upsert(record: TripRecord) {
        synchronized(LOCK) { write(TripHistory.upsert(read(), record)) }
    }

    fun remove(id: Long) {
        synchronized(LOCK) { write(TripHistory.remove(read(), id)) }
    }

    fun clear() {
        synchronized(LOCK) { file.delete() }
    }

    private fun read(): List<TripRecord> =
        if (file.exists()) TripCodec.decodeAll(file.readText()) else emptyList()

    private fun write(records: List<TripRecord>) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(TripCodec.encodeAll(records))
        if (!temp.renameTo(file)) {
            file.writeText(TripCodec.encodeAll(records))
            temp.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "trips.csv"
        val LOCK = Any()
    }
}
