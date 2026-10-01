package br.com.obdpulse.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Ntfy {

    suspend fun publish(topic: String, title: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Title", title)
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
            connection.outputStream.use { it.write(message.toByteArray(Charsets.UTF_8)) }
            val ok = connection.responseCode in 200..299
            connection.disconnect()
            ok
        } catch (e: IOException) {
            false
        }
    }
}
