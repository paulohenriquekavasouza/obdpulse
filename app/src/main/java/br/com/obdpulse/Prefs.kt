package br.com.obdpulse

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.obdpulse.obd.Keys

object Prefs {
    private const val FILE = "obd_pulse"
    private const val KEY_ADDRESS = "address"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_PROTOCOL = "protocol"
    private val DEFAULT_FAVORITES = listOf(Keys.BOOST, "0C", "0D", "05", "5C", "52", "0F", Keys.BATTERY)

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
