package app.bedrockhost.mobile.server

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the experimental Falcon Android ARM64 server from CraftNode's prerelease.
 * The binary is kept separately from NovaMine so switching engines never overwrites worlds
 * or the existing NovaMine installation.
 */
class FalconInstaller(private val filesDir: File) {
    data class InstallResult(val executable: File, val version: String)

    fun install(onProgress: (String) -> Unit = {}): InstallResult {
        val root = File(filesDir, "falcon-engine")
        val staging = File(filesDir, "falcon-engine-staging")
        if (staging.exists()) check(staging.deleteRecursively()) { "Could not clear Falcon staging directory." }
        check(staging.mkdirs()) { "Could not create Falcon staging directory." }
        try {
            val base = "https://github.com/mechy007/CRAFTNODE/releases/download/falcon-android-arm64-experimental"
            val checksumFile = File(staging, "FalconServer-android-arm64.sha256")
            val binary = File(staging, "FalconServer-android-arm64")
            onProgress("Downloading Falcon 1.26.52 Android ARM64 checksum…")
            download("$base/FalconServer-android-arm64.sha256", checksumFile, 1024L * 1024L)
            val expected = checksumFile.readText(Charsets.UTF_8).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            check(expected.matches(Regex("[0-9a-fA-F]{64}"))) { "Falcon checksum file is invalid." }
            onProgress("Downloading experimental Falcon 1.26.52 engine…")
            download("$base/FalconServer-android-arm64", binary, MAX_BINARY_BYTES)
            check(sha256(binary).equals(expected, ignoreCase = true)) {
                "Falcon SHA-256 verification failed. The downloaded engine was rejected."
            }
            // Keep the existing install until the new binary has downloaded and verified.
            val stagedBinary = File(staging, "FalconServer")
            check(binary.renameTo(stagedBinary)) { "Could not prepare Falcon executable." }
            Os.chmod(stagedBinary.absolutePath, 493) // 0755; keep the verified ELF executable after moving it to app-private files storage.
            check(stagedBinary.canExecute()) { "Android could not mark Falcon executable." }
            val previous = File(filesDir, "falcon-engine-previous")
            if (previous.exists()) check(previous.deleteRecursively()) { "Could not clear previous Falcon backup." }
            val hadPrevious = root.exists()
            if (hadPrevious) check(root.renameTo(previous)) { "Could not preserve the existing Falcon engine." }
            try {
                check(staging.renameTo(root)) { "Could not activate Falcon engine." }
                val installed = File(root, "FalconServer")
                check(installed.isFile && installed.canExecute()) { "Falcon post-install validation failed." }
                File(root, "INSTALL_INFO.txt").writeText(
                    "Falcon Android ARM64 experimental build\\nMinecraft Bedrock 1.26.52 / protocol 2193\\nInstall location: app-private files storage (not code_cache).\\n" +
                        "Source: Falcon-MC/Falcon v1.0.3\\nSHA-256 verified at install time.\\n" +
                        "Not yet validated on a physical Android device; keep a backup of worlds.\\n"
                )
                if (previous.exists()) previous.deleteRecursively()
                onProgress("Falcon downloaded and checksum-verified. Physical-device testing is still required.")
                return InstallResult(installed, "Falcon 1.26.52 experimental")
            } catch (failure: Exception) {
                if (root.exists()) root.deleteRecursively()
                if (hadPrevious && previous.exists()) previous.renameTo(root)
                throw failure
            }
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun download(url: String, destination: File, maxBytes: Long) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CraftNode-Android")
        }
        try {
            check(connection.responseCode in 200..299) {
                "Falcon download failed with HTTP ${connection.responseCode}. The experimental release may not be published yet."
            }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= maxBytes) { "Falcon download exceeded its safety size limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_BINARY_BYTES = 512L * 1024L * 1024L
    }
}
