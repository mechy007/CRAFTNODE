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
            ACTION_INSTALL, ACTION_INSTALL_FALCON -> {
                val installFalcon = intent.action == ACTION_INSTALL_FALCON
                startForeground(NOTIFICATION_ID, notification(if (installFalcon) "Installing experimental Falcon engine" else "Installing NovaMine Bedrock server engine"))
                thread(name = "CraftNode-Engine-Install") {
                    try {
                        if (installFalcon) {
                            FalconInstaller(filesDir).install { message -> log(message); updateNotification(message) }
                            log("Falcon experimental engine installed; physical-device compatibility is not yet verified.")
                            updateNotification("Falcon experimental engine installed")
                        } else {
                            PocketMineInstaller(codeCacheDir, assets).install { message -> log(message); updateNotification(message) }
                            log("NovaMine engine installation complete in Android code cache.")
                            updateNotification("NovaMine Bedrock engine installed")
                        }
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
            ACTION_COMMAND -> {
                startForeground(NOTIFICATION_ID, notification("Sending server console command"))
                val command = intent.getStringExtra(EXTRA_COMMAND)?.trim().orEmpty()
                if (command.isNotBlank() && command.length <= 512 && !command.contains('\n') && !command.contains('\r')) {
                    val running = process
                    if (running?.isAlive == true) {
                        runCatching {
                            running.outputStream.write((command.removePrefix("/") + "\n").toByteArray(Charsets.UTF_8))
                            running.outputStream.flush()
                            log("Console command sent: $command")
                        }.onFailure { log("Command failed: ${it.message ?: "unknown error"}") }
                    } else log("Command ignored: no active server process.")
                } else log("Command rejected: empty, too long, or contains a line break.")
            }
            ACTION_START -> {
                val requestedEngine = intent.getStringExtra(EXTRA_ENGINE_ID)
                val savedEngine = getSharedPreferences("craftnode_settings", MODE_PRIVATE)
                    .getString("selected_engine", "PocketMine-MP")
                // Recover if the Activity sends a stale/default ID while Falcon is the saved selection.
                val resolvedEngine = when {
                    requestedEngine == ENGINE_FALCON -> ENGINE_FALCON
                    savedEngine == "Falcon (experimental)" -> ENGINE_FALCON
                    requestedEngine == ENGINE_NOVAMINE || requestedEngine == null -> ENGINE_NOVAMINE
                    else -> requestedEngine
                }
                log("Start request received: requestedEngine=" + (requestedEngine ?: "missing") +
                    ", savedSelection=" + (savedEngine ?: "missing") + ", resolvedEngine=" + resolvedEngine)
                startForeground(NOTIFICATION_ID, notification("Starting " +
                    (if (resolvedEngine == ENGINE_FALCON) "Falcon experimental" else "NovaMine") + " server"))
                startEngine(intent.getStringExtra(EXTRA_PROFILE_ID) ?: "survival", resolvedEngine)
            }
            ACTION_STOP -> stopEngine()
            ACTION_RESTART -> {
                startForeground(NOTIFICATION_ID, notification("Restarting PocketMine-MP"))
                restartEngine(intent.getStringExtra(EXTRA_PROFILE_ID) ?: "survival", intent.getStringExtra(EXTRA_ENGINE_ID) ?: ENGINE_NOVAMINE)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startEngine(profileId: String, engineId: String = ENGINE_NOVAMINE) {
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
        if (engineId == ENGINE_FALCON) {
            startFalconEngine(safeId)
            return
        }
        if (engineId != ENGINE_NOVAMINE) {
            log("Start failed: unknown engine selection.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val engineRoot = File(codeCacheDir, "engine")
        val phar = File(engineRoot, "PocketMine-MP.phar")
        val bundledPhp = File(applicationInfo.nativeLibraryDir, "libphp.so")
        val extractedPhp = engineRoot.walkTopDown().firstOrNull {
            it.isFile && it.name == "php" && it.parentFile?.name?.startsWith("bin") == true
        } ?: engineRoot.walkTopDown().firstOrNull { it.isFile && it.name == "php" }
        val php = bundledPhp.takeIf { it.isFile && it.canExecute() } ?: extractedPhp
        if (!phar.isFile || php == null || !php.canExecute()) {
            log("Start failed: Bedrock server engine is not installed. Use Settings > Install Bedrock Engine first.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            val profileDir = File(filesDir, "worlds/$safeId").apply { mkdirs() }
            configureLocalXboxAuthentication(profileDir)
            val started = ProcessBuilder(php.absolutePath, phar.absolutePath, "--no-wizard")
                .directory(profileDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = profileDir.absolutePath
                    environment()["TMPDIR"] = File(cacheDir, "php-tmp").apply { mkdirs() }.absolutePath
                    val iniFile = engineRoot.walkTopDown().firstOrNull { it.isFile && it.name == "php.ini" }
                    if (iniFile != null) environment()["PHPRC"] = iniFile.absolutePath
                    val libraryDirs = engineRoot.walkTopDown()
                        .filter { it.isDirectory && (it.name == "lib" || it.name == "lib64") }
                        .map { it.absolutePath }
                        .toList()
                    if (libraryDirs.isNotEmpty()) environment()["LD_LIBRARY_PATH"] =
                        libraryDirs.joinToString(File.pathSeparator)
                }
                .start()
            process = started
            log("NovaMine process started for profile $safeId using ${if (php == bundledPhp) "APK-bundled PHP" else "extracted PHP fallback"}; waiting for server readiness.")
            logThread = thread(name = "CraftNode-Server-Logs") {
                started.inputStream.bufferedReader().useLines { lines -> lines.forEach { log(it) } }
                val exit = runCatching { started.waitFor() }.getOrNull()
                log("NovaMine exited with code ${exit ?: "unknown"}.")
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

    /**
     * This is a phone-hosted LAN test server. Disable server-side Xbox identity validation
     * so a Bedrock client is not rejected by the engine's Xbox-auth gate. Only use this
     * on a trusted LAN: player names are not authenticated by the server in this mode.
     */
    private fun configureLocalXboxAuthentication(profileDir: File) {
        val propertiesFile = File(profileDir, "server.properties")
        val existing = if (propertiesFile.isFile) propertiesFile.readLines(Charsets.UTF_8) else emptyList()
        var replaced = false
        val updated = existing.map { line ->
            if (line.substringBefore('#').trim().startsWith("xbox-auth=")) {
                replaced = true
                "xbox-auth=off"
            } else {
                line
            }
        }.toMutableList()
        if (!replaced) updated.add("xbox-auth=off")
        propertiesFile.writeText(updated.joinToString("\n") + "\n", Charsets.UTF_8)
        log("LAN compatibility: server-side Xbox authentication is OFF; use only on a trusted network because player identity is not verified.")
    }

    private fun startFalconEngine(profileId: String) {
        val executable = File(filesDir, "falcon-engine/FalconServer")
        if (!executable.isFile || !executable.canExecute()) {
            log("Falcon start failed: install the experimental Falcon engine from Settings first.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            val profileDir = File(filesDir, "worlds/$profileId").apply { mkdirs() }
            val started = ProcessBuilder(executable.absolutePath).directory(profileDir).redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = profileDir.absolutePath
                    environment()["TMPDIR"] = File(cacheDir, "falcon-tmp").apply { mkdirs() }.absolutePath
                }.start()
            process = started
            log("Experimental Falcon process launched from app-private files storage for $profileId. Readiness and gameplay remain unverified.")
            logThread = thread(name = "CraftNode-Falcon-Logs") {
                started.inputStream.bufferedReader().useLines { lines -> lines.forEach { log(it) } }
                val exit = runCatching { started.waitFor() }.getOrNull()
                log("Falcon exited with code ${exit ?: "unknown"}.")
                if (process === started) process = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            updateNotification("Falcon experimental process launched")
        } catch (e: Exception) {
            log("Falcon start failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
            process = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun restartEngine(profileId: String, engineId: String = ENGINE_NOVAMINE) {
        thread(name = "CraftNode-Server-Restart") {
            val running = process
            if (running != null && running.isAlive) {
                log("Restart requested: sending stop command.")
                runCatching {
                    running.outputStream.write("stop\n".toByteArray(Charsets.UTF_8))
                    running.outputStream.flush()
                }
                try {
                    if (!running.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                        log("Restart: graceful stop timed out; terminating process.")
                        running.destroy()
                        if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) running.destroyForcibly()
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log("Restart interrupted; server was not restarted.")
                    return@thread
                }
            }
            if (process === running) process = null
            startEngine(profileId, engineId)
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
        const val ACTION_INSTALL_FALCON = "app.bedrockhost.mobile.server.INSTALL_FALCON"
        const val ACTION_START = "app.bedrockhost.mobile.server.START"
        const val ACTION_COMMAND = "app.bedrockhost.mobile.server.COMMAND"
        const val ACTION_STOP = "app.bedrockhost.mobile.server.STOP"
        const val ACTION_RESTART = "app.bedrockhost.mobile.server.RESTART"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_ENGINE_ID = "engine_id"
        const val EXTRA_COMMAND = "command"
        const val ENGINE_NOVAMINE = "novamine"
        const val ENGINE_FALCON = "falcon"
        private const val CHANNEL_ID = "craftnode_server"
        private const val NOTIFICATION_ID = 401
    }
}