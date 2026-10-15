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

/** Manages the app-private PocketMine-MP process and installation task. */
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
            ACTION_INSTALL -> {
                startForeground(NOTIFICATION_ID, notification("Installing Bedrock server engine"))
                thread(name = "CraftNode-Engine-Install") {
                    try {
                        PocketMineInstaller(codeCacheDir).install { message ->
                            log(message)
                            updateNotification(message)
                        }
                        log("Engine installation complete in Android code cache. Start it once to finish first-run setup.")
                        updateNotification("PocketMine-MP installed")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    } catch (e: Exception) {
                        log("Engine installation failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
                        updateNotification("Engine installation failed; see console log")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
            ACTION_INSTALL_JAVA21 -> {
                startForeground(NOTIFICATION_ID, notification("Installing Java 21 Android ARM64"))
                thread(name = "CraftNode-Java21-Install") {
                    try {
                        val result = Java21AndroidArm64Installer(codeCacheDir).install { message ->
                            log(message)
                            updateNotification(message)
                        }
                        log("Java runtime verified from Android code cache: ${result.javaExecutable.absolutePath}")
                        log(result.versionOutput.trim())
                        updateNotification("Java 21 ARM64 runtime ready")
                    } catch (e: Exception) {
                        log("Java 21 install failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
                        updateNotification("Java install failed; see console log")
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification("Starting PocketMine-MP"))
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
        val engineRoot = File(codeCacheDir, "engine")
        val phar = File(engineRoot, "PocketMine-MP.phar")
        val php = engineRoot.walkTopDown().firstOrNull {
            it.isFile && it.name == "php" && it.parentFile?.name?.startsWith("bin") == true
        } ?: engineRoot.walkTopDown().firstOrNull { it.isFile && it.name == "php" }
        if (!phar.isFile || php == null || !php.canExecute()) {
            log("Start failed: PocketMine-MP is not installed. Use Settings > Install Bedrock Engine first.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            val profileDir = File(filesDir, "worlds/$safeId").apply { mkdirs() }
            val started = ProcessBuilder(php.absolutePath, phar.absolutePath, "--no-wizard")
                .directory(profileDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = profileDir.absolutePath
                    environment()["TMPDIR"] = File(cacheDir, "php-tmp").apply { mkdirs() }.absolutePath
                    val libraryDirs = engineRoot.walkTopDown()
                        .filter { it.isDirectory && (it.name == "lib" || it.name == "lib64") }
                        .map { it.absolutePath }
                        .toList()
                    if (libraryDirs.isNotEmpty()) environment()["LD_LIBRARY_PATH"] =
                        libraryDirs.joinToString(File.pathSeparator)
                }
                .start()
            process = started
            log("PocketMine-MP process started for profile $safeId; waiting for server readiness.")
            logThread = thread(name = "CraftNode-Server-Logs") {
                started.inputStream.bufferedReader().useLines { lines -> lines.forEach { log(it) } }
                val exit = runCatching { started.waitFor() }.getOrNull()
                log("PocketMine-MP exited with code ${exit ?: "unknown"}.")
                if (process === started) process = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            updateNotification("PocketMine-MP process running")
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
        log("Sending PocketMine-MP stop command.")
        runCatching {
            running.outputStream.write("stop\n".toByteArray(Charsets.UTF_8))
            running.outputStream.flush()
        }
        thread(name = "CraftNode-Stop-Wait") {
            try {
                if (!running.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                    log("Graceful stop timed out; terminating process.")
                    running.destroy()
                    if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) running.destroyForcibly()
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CraftNode server", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("CraftNode server")
            .setContentText(text.take(120))
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
        const val ACTION_INSTALL = "app.bedrockhost.mobile.server.INSTALL"
        const val ACTION_START = "app.bedrockhost.mobile.server.START"
        const val ACTION_INSTALL_JAVA21 = "app.bedrockhost.mobile.server.INSTALL_JAVA21"
        const val ACTION_STOP = "app.bedrockhost.mobile.server.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val CHANNEL_ID = "craftnode_server"
        private const val NOTIFICATION_ID = 401
    }
}