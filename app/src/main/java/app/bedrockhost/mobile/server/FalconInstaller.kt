package app.bedrockhost.mobile.server

import android.content.res.AssetManager
import java.io.File
import java.security.MessageDigest

/**
 * Validates Falcon packaged by Android's installer in nativeLibraryDir.
 * Android may mount app-private data directories with no-exec policy, so the
 * server ELF must not be downloaded to filesDir/code_cache and executed there.
 */
class FalconInstaller(
    private val nativeLibraryDir: File,
    private val assets: AssetManager
) {
    data class InstallResult(val executable: File, val version: String)

    fun install(onProgress: (String) -> Unit = {}): InstallResult {
        onProgress("Verifying bundled Falcon 1.26.52 Android ARM64 engine…")
        val binary = File(nativeLibraryDir, "libfalcon.so")
        check(binary.isFile && binary.canExecute()) {
            "Falcon is not bundled in this APK. Install the latest CraftNode build."
        }
        val expected = assets.open("falcon.sha256").bufferedReader(Charsets.UTF_8).use {
            it.readText().trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        }
        check(expected.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Bundled Falcon checksum asset is invalid."
        }
        check(sha256(binary).equals(expected, ignoreCase = true)) {
            "Bundled Falcon SHA-256 verification failed. The engine was rejected."
        }
        onProgress("Falcon bundled binary checksum-verified. Physical-device testing is still required.")
        return InstallResult(binary, "Falcon 1.26.52 experimental")
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
}
