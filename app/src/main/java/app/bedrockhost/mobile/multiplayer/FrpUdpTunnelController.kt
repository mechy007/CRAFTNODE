package app.bedrockhost.mobile.multiplayer

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Owns one FRP client process for CraftNode.
 *
 * The official FRP Android ARM64 executable must be packaged as libfrpc.so in
 * nativeLibraryDir. This controller deliberately fails closed when it is absent.
 * The relay must be configured separately; this class never creates a public relay.
 */
class FrpUdpTunnelController(
    context: Context,
    private val onLog: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val executable = File(appContext.applicationInfo.nativeLibraryDir, "libfrpc.so")
    private val configFile = File(appContext.filesDir, "remote-tunnel/frpc.toml")

    @Volatile private var process: Process? = null
    @Volatile private var reader: Thread? = null

    @Synchronized
    fun start(config: FrpUdpTunnelConfig): Boolean {
        if (process?.isAlive == true) {
            onLog("Remote tunnel start ignored: a tunnel process is already running.")
            return false
        }
        if (!executable.isFile || !executable.canExecute()) {
            onLog("Remote tunnel unavailable: the FRP client binary is missing or not executable in this installation.")
            return false
        }

        return try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(config.toToml(), Charsets.UTF_8)
            // filesDir is app-private; explicitly restrict the config containing the relay token.
            configFile.setReadable(false, false)
            configFile.setWritable(false, false)
            configFile.setReadable(true, true)
            configFile.setWritable(true, true)

            val started = ProcessBuilder(executable.absolutePath, "-c", configFile.absolutePath)
                .directory(configFile.parentFile)
                .redirectErrorStream(true)
                .start()
            process = started
            onLog("FRP client process launched; waiting for relay authentication and UDP proxy registration.")
            reader = thread(name = "CraftNode-FRP-Logs") {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        // Never expose the configured authentication token in app logs.
                        onLog(line.replace(config.authToken, "[REDACTED]").take(1000))
                    }
                }
                val exitCode = runCatching { started.waitFor() }.getOrNull()
                if (process === started) process = null
                onLog("FRP client exited with code ${exitCode ?: "unknown"}.")
            }
            true
        } catch (e: Exception) {
            process = null
            onLog("FRP client launch failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
            false
        }
    }

    @Synchronized
    fun stop() {
        val running = process
        if (running == null || !running.isAlive) {
            onLog("Remote tunnel stop requested: no active FRP process.")
            return
        }
        onLog("Stopping FRP client.")
        running.destroy()
        thread(name = "CraftNode-FRP-Stop") {
            try {
                if (!running.waitFor(3, TimeUnit.SECONDS)) running.destroyForcibly()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                running.destroyForcibly()
            }
            if (process === running) process = null
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    @Synchronized
    fun forgetLocalConfig() {
        stop()
        if (configFile.exists() && !configFile.delete()) {
            onLog("Warning: unable to delete the saved FRP config immediately.")
        }
    }
}
