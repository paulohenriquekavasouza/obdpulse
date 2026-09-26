package br.com.obdpulse

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.obdpulse.obd.Keys

object Prefs {
    private const val FILE = "obd_pulse"
    private const val KEY_ADDRESS = "address"
    const val KEY_FAVORITES = "favorites"
    private const val KEY_PROTOCOL = "protocol"
    const val KEY_CLUSTER = "cluster_metric"
    const val KEY_ORDER = "todos_order"
    const val CLUSTER_NONE = ""
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
}
