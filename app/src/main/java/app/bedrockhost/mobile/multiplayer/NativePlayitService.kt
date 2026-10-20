package app.bedrockhost.mobile.multiplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Foreground lifetime for the native Playit daemon, separate from the Bedrock engine. */
class NativePlayitService : Service() {
    private var controller: NativePlayitController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CraftNode Playit tunnel", NotificationManager.IMPORTANCE_LOW)
            )
        }
        controller = NativePlayitController(this) { message ->
            android.util.Log.i(TAG, message)
            getSharedPreferences(PREFS, 0).edit().putString(KEY_STATUS, message.take(800)).apply()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Handling Playit request"))
        when (intent?.action) {
            ACTION_START -> {
                val secret = intent.getStringExtra(EXTRA_SECRET)
                val started = controller?.start(secret) == true
                if (!started) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            ACTION_CLAIM -> {
                val url = controller?.beginClaim()
                if (url != null) {
                    getSharedPreferences(PREFS, 0).edit().putString(KEY_CLAIM_URL, url).apply()
                    getSharedPreferences(PREFS, 0).edit().putString(KEY_STATUS, "Approve the new CraftNode agent in your browser.").apply()
                }
            }
            ACTION_PROVISION -> {
                val secret = intent.getStringExtra(EXTRA_SECRET).orEmpty()
                controller?.provisionSecret(secret)
            }
            ACTION_STATUS -> {
                val status = controller?.status() ?: "Playit agent is not running."
                getSharedPreferences(PREFS, 0).edit().putString(KEY_STATUS, status).apply()
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(status))
            }
            ACTION_STOP -> {
                controller?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            ACTION_FORGET_SECRET -> {
                controller?.stop()
                controller?.forgetSecret()
                getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_AUTHORIZED, false).remove(KEY_CLAIM_URL).apply()
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
            .setContentTitle("CraftNode · Playit")
            .setContentText(message.take(120))
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        controller?.stop()
        controller = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.bedrockhost.mobile.multiplayer.PLAYIT_START"
        const val ACTION_PROVISION = "app.bedrockhost.mobile.multiplayer.PLAYIT_PROVISION"
        const val ACTION_CLAIM = "app.bedrockhost.mobile.multiplayer.PLAYIT_CLAIM"
        const val ACTION_STATUS = "app.bedrockhost.mobile.multiplayer.PLAYIT_STATUS"
        const val ACTION_STOP = "app.bedrockhost.mobile.multiplayer.PLAYIT_STOP"
        const val ACTION_FORGET_SECRET = "app.bedrockhost.mobile.multiplayer.PLAYIT_FORGET_SECRET"
        const val EXTRA_SECRET = "playit_secret"
        const val PREFS = "craftnode_playit"
        const val KEY_STATUS = "status"
        const val KEY_CLAIM_URL = "claim_url"
        const val KEY_AUTHORIZED = "authorized"
        private const val TAG = "CraftNodePlayit"
        private const val CHANNEL_ID = "craftnode_playit"
        private const val NOTIFICATION_ID = 403
    }
}
