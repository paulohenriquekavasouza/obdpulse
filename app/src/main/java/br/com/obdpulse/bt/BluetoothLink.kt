package br.com.obdpulse.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothLink private constructor(private val socket: BluetoothSocket) : Closeable {

    val input: InputStream get() = socket.inputStream
    val output: OutputStream get() = socket.outputStream

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @SuppressLint("MissingPermission")
        fun open(adapter: BluetoothAdapter, address: String): BluetoothLink {
            val device = adapter.getRemoteDevice(address)
            val factories = listOf<() -> BluetoothSocket>(
                { device.createRfcommSocketToServiceRecord(SPP_UUID) },
                { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
                {
                    device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                },
            )
            var lastError: Exception? = null
            for (factory in factories) {
                val socket = try {
                    factory()
                } catch (e: SecurityException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    continue
                }
                try {
                    socket.connect()
                    return BluetoothLink(socket)
                } catch (e: IOException) {
                    lastError = e
                    try {
                        socket.close()
                    } catch (_: IOException) {
                    }
                }
            }
            throw IOException("Não foi possível conectar ao adaptador. Verifique se ele está pareado e ligado.", lastError)
        }
    }
}
