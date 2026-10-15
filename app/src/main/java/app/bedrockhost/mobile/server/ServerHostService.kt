package app.bedrockhost.mobile.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * App-owned process supervisor. It starts only an engine installed in app-private storage.
 * The Bedrock engine installer is not wired yet, so missing runtime is reported honestly.
 */
class ServerHostService : Service() {
    @Volatile private var process: Process? = null
    private var logThread: Thread? = null
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val logFile by lazy { File(filesDir, "logs/server-console.log").apply { parentFile?.mkdirs() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification("Preparing server runtime"))
                startEngine(intent.getStringExtra(EXTRA_PROFILE_ID) ?: "survival")
            }
            ACTION_STOP -> stopEngine()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startEngine(profileId: String) {
        if (process?.isAlive == true) {
            log("Start ignored: a server process is already running.")
            return
        }
        val safeId = profileId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
        if (safeId == null) {
            log("Start failed: invalid server profile ID.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val executable = File(filesDir, "engine/bin/server")
        if (!executable.isFile || !executable.canExecute()) {
            log("Start failed: no verified Android-compatible Bedrock engine is installed.")
            log("Expected engine path: ${executable.absolutePath}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            val world = File(filesDir, "worlds/$safeId").apply { mkdirs() }
            val started = ProcessBuilder(executable.absolutePath, "--world-dir", world.absolutePath)
                .directory(File(filesDir, "engine"))
                .redirectErrorStream(true)
                .start()
            process = started
            log("Engine process started for profile $safeId.")
            logThread = thread(name = "CraftNode-Server-Logs") {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> log(line) }
                }
                val exit = runCatching { started.waitFor() }.getOrNull()
                log("Engine process exited with code ${exit ?: "unknown"}.")
                if (process === started) process = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            updateNotification("Server process started; waiting for engine readiness")
        } catch (e: Exception) {
            log("Start failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
            process = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @Synchronized
    private fun stopEngine() {
        val running = process
        if (running == null || !running.isAlive) {
            log("Stop requested: no active server process.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        log("Stopping server process.")
        running.destroy()
        thread(name = "CraftNode-Stop-Wait") {
            try {
                if (!running.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                    log("Graceful stop timed out; terminating process.")
                    running.destroyForcibly()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun log(message: String) {
        val line = "${System.currentTimeMillis()} $message"
        synchronized(logFile) { logFile.appendText(line + "\n") }
        listeners.forEach { listener -> runCatching { listener(line) } }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CraftNode server", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("CraftNode server")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onDestroy() {
        process?.let { if (it.isAlive) it.destroy() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.bedrockhost.mobile.server.START"
        const val ACTION_STOP = "app.bedrockhost.mobile.server.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val CHANNEL_ID = "craftnode_server"
        private const val NOTIFICATION_ID = 401
    }
}