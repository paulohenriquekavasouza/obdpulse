package br.com.obdpulse

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.obdpulse.obd.Keys
import br.com.obdpulse.obd.TripStats

data class Records(
    val best0100Ms: Long? = null,
    val maxSpeed: Double? = null,
    val maxRpm: Double? = null,
    val maxBoost: Double? = null,
)

object Prefs {
    private const val FILE = "obd_pulse"
    private const val KEY_ADDRESS = "address"
    const val KEY_FAVORITES = "favorites"
    private const val KEY_PROTOCOL = "protocol"
    const val KEY_CLUSTER = "cluster_metric"
    const val KEY_ORDER = "todos_order"
    private const val KEY_AUTO = "auto_connect"
    private const val KEY_FUEL_PRICE = "fuel_price"
    private const val KEY_TANK = "tank_liters"
    private const val KEY_REC_0100 = "rec_0100"
    private const val KEY_REC_VMAX = "rec_vmax"
    private const val KEY_REC_RPM = "rec_rpm"
    private const val KEY_REC_BOOST = "rec_boost"
    const val CLUSTER_NONE = ""
    const val DEFAULT_TANK = 47.0f
    const val DEFAULT_FUEL_PRICE = 5.89f
    private val DEFAULT_FAVORITES = listOf(Keys.BOOST, "0C", "0D", "05", Keys.CONSUMPTION, "77", "52", "42")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun address(context: Context): String? = prefs(context).getString(KEY_ADDRESS, null)

    fun saveAddress(context: Context, address: String) {
        prefs(context).edit { putString(KEY_ADDRESS, address) }
    }

    fun protocol(context: Context): Char? = prefs(context).getString(KEY_PROTOCOL, null)?.firstOrNull()

    fun saveProtocol(context: Context, protocol: Char) {
        prefs(context).edit { putString(KEY_PROTOCOL, protocol.toString()) }
    }

    fun clusterMetric(context: Context): String =
        prefs(context).getString(KEY_CLUSTER, CLUSTER_NONE) ?: CLUSTER_NONE

    fun saveClusterMetric(context: Context, key: String) {
        prefs(context).edit { putString(KEY_CLUSTER, key) }
    }

    fun observe(context: Context, onChanged: (String?) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> onChanged(key) }
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun removeObserver(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun order(context: Context): List<String> =
        prefs(context).getString(KEY_ORDER, null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    fun saveOrder(context: Context, keys: List<String>) {
        prefs(context).edit { putString(KEY_ORDER, keys.joinToString(",")) }
    }

    fun orderedKeys(saved: List<String>, available: List<String>): List<String> =
        saved.filter { it in available } + available.filter { it !in saved }

    fun favorites(context: Context): List<String> =
        prefs(context).getString(KEY_FAVORITES, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: DEFAULT_FAVORITES

    fun toggleFavorite(context: Context, key: String): List<String> {
        val list = favorites(context).toMutableList()
        if (!list.remove(key)) list += key
        prefs(context).edit { putString(KEY_FAVORITES, list.joinToString(",")) }
        return list
    }

    fun autoConnect(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoConnect(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO, enabled) }
    }

    fun fuelPrice(context: Context): Float = prefs(context).getFloat(KEY_FUEL_PRICE, DEFAULT_FUEL_PRICE)

    fun saveFuelPrice(context: Context, value: Float) {
        prefs(context).edit { putFloat(KEY_FUEL_PRICE, value) }
    }

    fun tankLiters(context: Context): Float = prefs(context).getFloat(KEY_TANK, DEFAULT_TANK)

    fun saveTankLiters(context: Context, value: Float) {
        prefs(context).edit { putFloat(KEY_TANK, value) }
    }

    fun records(context: Context): Records {
        val p = prefs(context)
        return Records(
            best0100Ms = p.getLong(KEY_REC_0100, 0L).takeIf { it > 0L },
            maxSpeed = p.getFloat(KEY_REC_VMAX, 0f).takeIf { it > 0f }?.toDouble(),
            maxRpm = p.getFloat(KEY_REC_RPM, 0f).takeIf { it > 0f }?.toDouble(),
            maxBoost = p.getFloat(KEY_REC_BOOST, -999f).takeIf { it > -900f }?.toDouble(),
        )
    }

    fun updateRecords(context: Context, trip: TripStats) {
        val p = prefs(context)
        p.edit {
            trip.bestZeroTo100Ms?.let { v ->
                val cur = p.getLong(KEY_REC_0100, 0L)
                if (cur <= 0L || v < cur) putLong(KEY_REC_0100, v)
            }
            trip.maxSpeed?.let { if (it > p.getFloat(KEY_REC_VMAX, 0f)) putFloat(KEY_REC_VMAX, it.toFloat()) }
            trip.maxRpm?.let { if (it > p.getFloat(KEY_REC_RPM, 0f)) putFloat(KEY_REC_RPM, it.toFloat()) }
            trip.maxBoost?.let { if (it > p.getFloat(KEY_REC_BOOST, -999f)) putFloat(KEY_REC_BOOST, it.toFloat()) }
        }
    }

    fun resetRecords(context: Context) {
        prefs(context).edit {
            remove(KEY_REC_0100); remove(KEY_REC_VMAX); remove(KEY_REC_RPM); remove(KEY_REC_BOOST)
        }
    }
}
