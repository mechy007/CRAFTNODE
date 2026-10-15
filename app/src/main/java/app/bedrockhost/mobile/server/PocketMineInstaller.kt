package app.bedrockhost.mobile.server

import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

/**
 * Installs a pinned PocketMine-MP release and its official Android ARM64 PHP runtime.\n * The caller must supply Android codeCacheDir because filesDir may be non-executable on some Android builds.
 * This is a community Bedrock server implementation, not Mojang's official vanilla server.
 */
class PocketMineInstaller(private val filesDir: File) {
    data class InstallResult(val phpExecutable: File, val serverPhar: File, val version: String)

    fun install(onProgress: (String) -> Unit = {}): InstallResult {
        val engineRoot = File(filesDir, "engine")
        val staging = File(filesDir, "engine-install-staging")
        if (staging.exists()) staging.deleteRecursively()
        check(staging.mkdirs()) { "Cannot create temporary engine directory." }

        val phpArchive = File(staging, "php-runtime.tar.gz")
        val serverPhar = File(staging, "PocketMine-MP.phar")
        onProgress("Downloading verified Android ARM64 PHP runtime…")
        downloadVerified(
            "https://github.com/pmmp/PHP-Binaries/releases/download/pm5-php-8.4-latest/PHP-8.4-Android-arm64-PM5.tar.gz",
            phpArchive,
            "d8867966340121f821591b9bb29c80a58ad77abd6cc8e0e44d1cbbb3aaedd70c"
        )
        onProgress("Downloading PocketMine-MP 5.44.3…")
        downloadVerified(
            "https://github.com/pmmp/PocketMine-MP/releases/download/5.44.3/PocketMine-MP.phar",
            serverPhar,
            "d8b4d87a64124d83659f629c87d6a8e0e142192f6c719fe4684272ff44105539"
        )

        val runtimeDir = File(staging, "runtime").apply { mkdirs() }
        onProgress("Extracting runtime safely…")
        extractTarGz(phpArchive, runtimeDir)
        val php = runtimeDir.walkTopDown().firstOrNull {
            it.isFile && it.name == "php" && it.parentFile?.name?.startsWith("bin") == true
        } ?: runtimeDir.walkTopDown().firstOrNull { it.isFile && it.name == "php" }
        check(php != null) { "The downloaded PHP runtime did not contain a PHP executable." }

        val target = File(staging, "complete")
        check(target.mkdirs()) { "Cannot create final engine staging directory." }
        val copiedRuntime = File(target, "runtime")
        check(runtimeDir.renameTo(copiedRuntime)) { "Could not prepare runtime files." }
        val targetPhp = copiedRuntime.walkTopDown().firstOrNull {
            it.isFile && it.name == "php" && it.parentFile?.name?.startsWith("bin") == true
        } ?: copiedRuntime.walkTopDown().firstOrNull { it.isFile && it.name == "php" }
        check(targetPhp != null) { "PHP executable disappeared during installation." }
        Os.chmod(targetPhp.absolutePath, 365)\n        check(targetPhp.canExecute()) { "PHP executable permission could not be set." }
        val targetPhar = File(target, "PocketMine-MP.phar")
        check(serverPhar.copyTo(targetPhar, overwrite = true).length() > 1_000_000L) {
            "PocketMine-MP download is incomplete."
        }
        File(target, "INSTALL_INFO.txt").writeText(
            "PocketMine-MP 5.44.3\nPHP 8.4 Android ARM64 runtime\n" +
                "Source: official upstream release assets\n" +
                "Installed by CraftNode. This is third-party Bedrock server software.\n"
        )

        // Activate the new engine transactionally: keep the previous install until the
        // staged engine has been moved into place, and restore it if activation fails.
        val previous = File(filesDir, "engine-previous")
        if (previous.exists()) check(previous.deleteRecursively()) { "Could not clear the previous engine backup." }
        val hadPrevious = engineRoot.exists()
        if (hadPrevious) check(engineRoot.renameTo(previous)) { "Could not preserve the current engine install." }
        try {
            check(target.renameTo(engineRoot)) { "Could not activate the installed engine." }
            val installedRelativePath = targetPhp.relativeTo(copiedRuntime).invariantSeparatorsPath
            val installedPhp = File(engineRoot, "runtime/$installedRelativePath")
            check(installedPhp.isFile && installedPhp.canExecute()) { "PHP runtime failed its post-install check." }\n            check(installedPhp.parentFile?.canonicalPath?.startsWith(engineRoot.canonicalPath) == true) { "PHP runtime escaped the engine directory." }
            check(File(engineRoot, "PocketMine-MP.phar").length() > 1_000_000L) {
                "PocketMine-MP failed its post-install check."
            }
            if (previous.exists()) previous.deleteRecursively()
        } catch (failure: Exception) {
            if (engineRoot.exists()) engineRoot.deleteRecursively()
            if (hadPrevious && previous.exists()) previous.renameTo(engineRoot)
            throw failure
        } finally {
            staging.deleteRecursively()
        }

        val installedPhp = File(engineRoot, "runtime/" + targetPhp.relativeTo(copiedRuntime).invariantSeparatorsPath)
        onProgress("PocketMine-MP files installed. First startup and in-game LAN test are still required.")
        return InstallResult(installedPhp, File(engineRoot, "PocketMine-MP.phar"), "5.44.3")
    }

    private fun downloadVerified(url: String, destination: File, expectedSha256: String) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CraftNode-Android")
        }
        try {
            check(connection.responseCode in 200..299) { "Download failed with HTTP ${connection.responseCode}." }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 200L * 1024L * 1024L) { "Download exceeded the 200 MB safety limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        check(sha256(destination) == expectedSha256) {
            "SHA-256 verification failed for ${destination.name}; the downloaded file was discarded."
        }
    }

    private fun extractTarGz(archive: File, destination: File) {
        val root = destination.canonicalFile
        TarArchiveInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            var entries = 0
            while (true) {
                val entry = tar.nextTarEntry ?: break
                entries++
                check(entries <= 30_000) { "Runtime archive contains too many entries." }
                val output = File(root, entry.name).canonicalFile
                check(output.path == root.path || output.path.startsWith(root.path + File.separator)) {
                    "Unsafe path found in runtime archive."
                }
                if (entry.isSymbolicLink) {
                    val target = File(output.parentFile, entry.linkName).canonicalFile
                    check(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
                        "Unsafe symbolic link found in runtime archive."
                    }
                    output.parentFile?.mkdirs()
                    if (output.exists() || output.isFile) output.delete()
                    Os.symlink(entry.linkName, output.absolutePath)
                } else if (entry.isDirectory) {
                    check(output.mkdirs() || output.isDirectory) { "Cannot create runtime directory." }
                } else if (entry.isFile) {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { out -> tar.copyTo(out) }
                    if ((entry.mode and 73) != 0) runCatching { Os.chmod(output.absolutePath, entry.mode and 511) }
                } else {
                    // Device nodes, sockets and hard links are deliberately not extracted.
                    check(!entry.isLink) { "Hard links are not supported by the safe runtime installer." }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}