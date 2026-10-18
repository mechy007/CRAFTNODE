package app.bedrockhost.mobile.multiplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Foreground lifetime for the FRP client, separate from the Bedrock engine process. */
class FrpTunnelService : Service() {
    private var controller: FrpUdpTunnelController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CraftNode remote tunnel", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification("Starting remote tunnel"))
                val config = runCatching {
                    FrpUdpTunnelConfig(
                        relayHost = intent.getStringExtra(EXTRA_RELAY_HOST).orEmpty(),
                        relayControlPort = intent.getIntExtra(EXTRA_CONTROL_PORT, -1),
                        authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN).orEmpty(),
                        publicUdpPort = intent.getIntExtra(EXTRA_PUBLIC_UDP_PORT, -1),
                        localUdpPort = intent.getIntExtra(EXTRA_LOCAL_UDP_PORT, FrpUdpTunnelConfig.DEFAULT_BEDROCK_PORT)
                    )
                }.getOrElse { error ->
                    updateNotification("Invalid tunnel configuration")
                    android.util.Log.w("CraftNodeTunnel", "Tunnel configuration rejected: ${error.message}")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val activeController = controller ?: FrpUdpTunnelController(this) { message ->
                    // Controller redacts the secret before emitting process output.
                    android.util.Log.i("CraftNodeTunnel", message)
                    updateNotification(message)
                }.also { controller = it }
                if (!activeController.start(config)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    updateNotification("FRP client running; verifying relay connection")
                }
            }
            ACTION_STOP -> {
                controller?.forgetLocalConfig()
                controller = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("CraftNode remote tunnel")
            .setContentText(message.take(120))
            .setOngoing(true)
            .build()

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }

    override fun onDestroy() {
        controller?.forgetLocalConfig()
        controller = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.bedrockhost.mobile.multiplayer.TUNNEL_START"
        const val ACTION_STOP = "app.bedrockhost.mobile.multiplayer.TUNNEL_STOP"
        const val EXTRA_RELAY_HOST = "tunnel_relay_host"
        const val EXTRA_CONTROL_PORT = "tunnel_control_port"
        const val EXTRA_AUTH_TOKEN = "tunnel_auth_token"
        const val EXTRA_PUBLIC_UDP_PORT = "tunnel_public_udp_port"
        const val EXTRA_LOCAL_UDP_PORT = "tunnel_local_udp_port"
        private const val CHANNEL_ID = "craftnode_remote_tunnel"
        private const val NOTIFICATION_ID = 402
    }
}
