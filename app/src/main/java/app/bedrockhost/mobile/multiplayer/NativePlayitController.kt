package app.bedrockhost.mobile.multiplayer

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Owns the pinned Playit daemon process and its private IPC socket.
 * The daemon secret is written only to app-private storage with owner-only permissions.
 */
class NativePlayitController(context: Context, private val onStatus: (String) -> Unit) {
    private val app = context.applicationContext
    private val daemon = File(app.applicationInfo.nativeLibraryDir, "libplayitd.so")
    private val cli = File(app.applicationInfo.nativeLibraryDir, "libplayit_cli.so")
    private val socketPath = File(app.filesDir, "playit/playitd.sock").absolutePath
    private val secretPath = File(app.filesDir, "playit/secret.key")
    private val workDir = File(app.filesDir, "playit")
    @Volatile private var process: Process? = null
    @Volatile private var logThread: Thread? = null

    @Synchronized
    fun start(secret: String? = null): Boolean {
        if (process?.isAlive == true) {
            onStatus("Playit daemon is already running.")
            if (!secret.isNullOrBlank()) provisionSecret(secret)
            return true
        }
        if (!daemon.isFile || !daemon.canExecute()) {
            onStatus("Native Playit daemon is missing or not executable in this APK.")
            return false
        }
        return try {
            check(workDir.mkdirs() || workDir.isDirectory) { "Cannot create private Playit directory." }
            if (secretPath.exists()) {
                secretPath.setReadable(false, false); secretPath.setWritable(false, false)
            }
            val args = mutableListOf(daemon.absolutePath, "--socket-path", socketPath, "--secret-path", secretPath.absolutePath)
            val p = ProcessBuilder(args).directory(workDir).redirectErrorStream(true).start()
            process = p
            onStatus("Playit daemon process started; checking native IPC status.")
            logThread = thread(name = "CraftNode-Playit-Logs") {
                try {
                    p.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> onStatus("Playit: ${line.take(500)}") }
                    }
                } catch (_: Exception) { }
                finally {
                    val code = runCatching { p.waitFor() }.getOrNull()
                    if (process === p) process = null
                    onStatus("Playit daemon exited (code ${code ?: "unknown"}).")
                }
            }
            if (!secret.isNullOrBlank()) {
                thread(name = "CraftNode-Playit-Provision") {
                    Thread.sleep(500)
                    provisionSecret(secret)
                }
            }
            true
        } catch (e: Exception) {
            process = null
            onStatus("Native Playit launch failed: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
            false
        }
    }

    /**
     * Starts Playit's official self-managed claim flow. The browser approval is done by the user;
     * the CLI waits for approval and returns the newly generated secret, which is provisioned
     * to this app's own daemon. No Termux installation or Termux agent is reused.
     */
    @Synchronized
    fun beginClaim(): String? {
        if (!cli.isFile || !cli.canExecute()) {
            onStatus("CraftNode's bundled Playit CLI is missing or not executable.")
            return null
        }
        return runCatching {
            check(workDir.mkdirs() || workDir.isDirectory) { "Cannot create private Playit directory." }
            val codeProcess = ProcessBuilder(cli.absolutePath, "claim", "generate")
                .directory(workDir).redirectErrorStream(true).start()
            val code = codeProcess.inputStream.bufferedReader().readText().trim().also {
                check(codeProcess.waitFor() == 0 && it.matches(Regex("[0-9a-fA-F]{10}"))) {
                    "Playit CLI could not generate a claim code."
                }
            }
            val urlProcess = ProcessBuilder(cli.absolutePath, "claim", "url", code, "--name", "CraftNode", "--type", "self-managed")
                .directory(workDir).redirectErrorStream(true).start()
            val url = urlProcess.inputStream.bufferedReader().readText().trim().also {
                check(urlProcess.waitFor() == 0 && it.startsWith("https://playit.gg/claim/")) {
                    "Playit CLI did not return a valid claim URL."
                }
            }
            onStatus("Approve the new CraftNode agent in your browser: $url")
            thread(name = "CraftNode-Playit-Claim") {
                runCatching {
                    val exchange = ProcessBuilder(cli.absolutePath, "claim", "exchange", code, "--wait", "300")
                        .directory(workDir).redirectErrorStream(true).start()
                    val output = exchange.inputStream.bufferedReader().readText().trim()
                    check(exchange.waitFor() == 0) { "Playit claim was not approved or timed out." }
                    val secret = output.lineSequence().map { it.trim() }.lastOrNull { it.isNotBlank() }
                        ?: error("Playit returned an empty agent key.")
                    check(secret.length <= 4096 && !secret.contains(" ") && !secret.contains("://")) {
                        "Playit CLI returned an unexpected claim response."
                    }
                    check(start()) { "Could not start the CraftNode Playit daemon." }
                    check(provisionSecret(secret)) { "Playit did not accept the new CraftNode agent key." }
                    onStatus("CraftNode agent authorized. Refresh status to check its tunnel state.")
                }.onFailure { onStatus("CraftNode agent claim failed: ${it.message ?: "unknown error"}") }
            }
            url
        }.getOrElse {
            onStatus("Could not start CraftNode agent claim: ${it.message ?: "unknown error"}")
            null
        }
    }

    fun provisionSecret(secret: String): Boolean {
        if (secret.isBlank() || secret.length > 4096 || secret.any { it == '\n' || it == '\r' }) {
            onStatus("Playit secret was rejected. Paste the secret key exactly as provided.")
            return false
        }
        return runCatching {
            val reply = request(JSONObject().put("type", "set_secret").put("secret", secret))
            val response = reply.optJSONObject("data")?.optJSONObject("response")
            val data = response?.optJSONObject("data")
            val accepted = data?.optBoolean("accepted", false) == true
            app.getSharedPreferences(NativePlayitService.PREFS, 0).edit()
                .putBoolean(NativePlayitService.KEY_AUTHORIZED, accepted).apply()
            onStatus(if (accepted) "CraftNode agent verified. Checking tunnel readiness…" else "Playit did not accept the secret. Check the key and daemon logs.")
            accepted
        }.getOrElse {
            onStatus("Playit daemon is not ready to accept authorization yet.")
            false
        }
    }

    fun status(): String = runCatching {
        val response = request(JSONObject().put("type", "get_state"))
        val envelope = response.optJSONObject("data")?.optJSONObject("response") ?: response
        val state = envelope.optJSONObject("data")
        val stateName = state?.optString("state", "unknown") ?: "unknown"
        val data = state?.optJSONObject("data")
        val tunnels = data?.optJSONArray("tunnels")
        val endpoints = mutableListOf<String>()
        if (tunnels != null) {
            for (i in 0 until tunnels.length()) {
                val tunnel = tunnels.optJSONObject(i) ?: continue
                val address = tunnel.optString("display_address", "")
                if (address.isNotBlank()) endpoints += address
            }
        }
        val endpointText = endpoints.distinct().joinToString("\n")
        app.getSharedPreferences(NativePlayitService.PREFS, 0).edit()
            .putString(NativePlayitService.KEY_ENDPOINTS, endpointText)
            .putString(NativePlayitService.KEY_TUNNEL_STATE, stateName)
            .apply()
        if (endpoints.isEmpty()) "Playit state: $stateName. Waiting for a public tunnel endpoint."
        else "Playit state: $stateName. Public endpoint available."
    }.getOrElse { "Playit status unavailable: ${it.javaClass.simpleName}." }

    @Synchronized
    fun stop() {
        val p = process
        if (p == null || !p.isAlive) {
            onStatus("Playit daemon is not running.")
            return
        }
        runCatching { request(JSONObject().put("type", "stop")) }
        thread(name = "CraftNode-Playit-Stop") {
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroy()
                if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                p.destroyForcibly()
            }
            if (process === p) process = null
            onStatus("Playit daemon stopped.")
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    /** Protocol is newline-delimited JSON; the first frame is the server hello. */
    private fun request(payload: JSONObject): JSONObject {
        val socket = LocalSocket()
        try {
            socket.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
            socket.soTimeout = 2500
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
            // Drain hello frame before sending an IPC v2 request.
            reader.readLine() ?: error("No Playit IPC hello")
            writer.write(JSONObject().put("ipc_version", 2).put("request_id", 1).put("request", payload).toString())
            writer.newLine()
            writer.flush()
            return JSONObject(reader.readLine() ?: error("No Playit IPC response"))
        } finally {
            runCatching { socket.close() }
        }
    }

    fun forgetSecret() {
        if (secretPath.exists()) {
            secretPath.setReadable(false, false)
            secretPath.setWritable(false, false)
            secretPath.delete()
        }
    }
}
