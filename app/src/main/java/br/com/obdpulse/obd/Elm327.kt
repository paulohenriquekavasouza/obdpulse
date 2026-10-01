package br.com.obdpulse.obd

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class Elm327(
    private val input: InputStream,
    private val output: OutputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val replies = Channel<String>(Channel.UNLIMITED)
    private val mutex = Mutex()

    fun start(scope: CoroutineScope): Job = scope.launch(ioDispatcher) {
        val buffer = StringBuilder()
        val chunk = ByteArray(512)
        try {
            while (isActive) {
                val count = input.read(chunk)
                if (count < 0) break
                for (i in 0 until count) {
                    when (val c = (chunk[i].toInt() and 0xFF).toChar()) {
                        '>' -> {
                            replies.trySend(buffer.toString())
                            buffer.setLength(0)
                        }
                        '\u0000' -> Unit
                        else -> buffer.append(c)
                    }
                }
            }
            replies.close(IOException("Conexão com o adaptador encerrada"))
        } catch (e: IOException) {
            replies.close(e)
        }
    }

    suspend fun send(command: String, timeoutMs: Long = DEFAULT_TIMEOUT): String = mutex.withLock {
        while (replies.tryReceive().isSuccess) Unit
        withContext(ioDispatcher) {
            output.write("$command\r".toByteArray(Charsets.US_ASCII))
            output.flush()
        }
        withTimeoutOrNull(timeoutMs) { replies.receive() } ?: throw ElmTimeoutException(command)
    }

    companion object {
        const val DEFAULT_TIMEOUT = 2_000L
    }
}
