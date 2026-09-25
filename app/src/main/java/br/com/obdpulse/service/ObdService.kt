package br.com.obdpulse.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import br.com.obdpulse.ObdManager
import br.com.obdpulse.R
import br.com.obdpulse.obd.ObdState
import br.com.obdpulse.obd.ObdStatus
import br.com.obdpulse.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ObdService : Service() {

    private val scope = MainScope()
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ObdManager.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        if (address == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ObdManager.connect(this, address)
        if (!startInForeground(getString(R.string.notification_connecting))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!watching) {
            watching = true
            scope.launch {
                ObdManager.state
                    .map { it.status to it.milOn }
                    .distinctUntilChanged()
                    .collect { render(ObdManager.state.value) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(state: ObdState) {
        when (state.status) {
            ObdStatus.DISCONNECTED, ObdStatus.ERROR -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ObdStatus.CONNECTING -> notify(getString(R.string.notification_connecting))
            ObdStatus.INITIALIZING -> notify(getString(R.string.notification_initializing))
            ObdStatus.CONNECTED -> notify(
                if (state.milOn == true) getString(R.string.notification_connected_mil)
                else getString(R.string.notification_connected),
            )
        }
    }

    private fun startInForeground(text: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, build(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, build(text))
        }
        true
    } catch (_: RuntimeException) {
        false
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ObdService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_obd)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_obd), getString(R.string.disconnect), stop,
                ).build(),
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "obd"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_ADDRESS = "address"
        private const val ACTION_STOP = "br.com.obdpulse.STOP"

        fun start(context: Context, address: String) {
            val canRunInForeground = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!canRunInForeground) {
                ObdManager.connect(context, address)
                return
            }
            val intent = Intent(context, ObdService::class.java).putExtra(EXTRA_ADDRESS, address)
            try {
                context.startForegroundService(intent)
            } catch (_: RuntimeException) {
                ObdManager.connect(context, address)
            }
        }
    }
}
