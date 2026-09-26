package br.com.obdpulse

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsOrderTest {

    @Test
    fun keepsSavedOrderThenAppendsNewInGivenOrder() {
        val saved = listOf("0D", "0C", "05")
        val available = listOf("0C", "0D", "05", "0B", "77")
        assertEquals(listOf("0D", "0C", "05", "0B", "77"), Prefs.orderedKeys(saved, available))
    }

    @Test
    fun dropsSavedKeysThatAreNotAvailable() {
        val saved = listOf("0D", "GONE", "0C")
        val available = listOf("0C", "0D")
        assertEquals(listOf("0D", "0C"), Prefs.orderedKeys(saved, available))
    }

    @Test
    fun emptySavedKeepsAvailableOrder() {
        val available = listOf("0C", "0D", "05")
        assertEquals(available, Prefs.orderedKeys(emptyList(), available))
    }
}
