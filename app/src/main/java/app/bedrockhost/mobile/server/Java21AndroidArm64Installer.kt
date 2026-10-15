package app.bedrockhost.mobile.server

import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/**
 * Installs a checksum-pinned OpenJDK 21 runtime compiled for Android ARM64.
 * This installs the runtime only; it does not claim PowerNukkitX is Android-supported.\n * The caller must supply Android codeCacheDir to avoid non-executable filesDir restrictions.
 */
class Java21AndroidArm64Installer(private val filesDir: File) {
    data class InstallResult(val javaExecutable: File, val versionOutput: String)

    fun install(onProgress: (String) -> Unit = {}): InstallResult {
        check(android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "aarch64" }) {
            "Java 21 Android ARM64 runtime requires a 64-bit ARM device."
        }

        val root = File(filesDir, "java21-arm64")
        val staging = File(filesDir, "java21-install-staging")
        if (staging.exists()) check(staging.deleteRecursively()) { "Could not clear old Java staging directory." }
        check(staging.mkdirs()) { "Cannot create Java staging directory." }
        val archive = File(staging, "jre21-android-arm64.tar.xz")
        try {
            onProgress("Downloading verified Java 21 Android ARM64 runtime…")
            downloadVerified(ASSET_URL, archive, ASSET_SHA256)
            val extracted = File(staging, "runtime").apply { mkdirs() }
            onProgress("Extracting Java runtime safely…")
            extractTarXz(archive, extracted)

            val java = extracted.walkTopDown().maxDepth(5).firstOrNull {
                it.isFile && it.name == "java" && it.parentFile?.name == "bin"
            } ?: error("The archive did not contain a Java executable.")
            Os.chmod(java.absolutePath, 365)\n            check(java.canExecute()) { "Java executable permission could not be set." }

            onProgress("Checking Java runtime version…")
            val libs = extracted.walkTopDown().filter {
                it.isDirectory && (it.name == "lib" || it.name == "lib64")
            }.map { it.absolutePath }.toList()
            val output = runCatching {
                ProcessBuilder(java.absolutePath, "-version")
                    .directory(extracted)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["HOME"] = filesDir.absolutePath
                        if (libs.isNotEmpty()) environment()["LD_LIBRARY_PATH"] =
                            (libs + (environment()["LD_LIBRARY_PATH"] ?: "")).filter { it.isNotBlank() }
                                .distinct().joinToString(File.pathSeparator)
                    }
                    .start().let { process ->
                        val text = process.inputStream.bufferedReader().use { it.readText().take(8_000) }
                        check(process.waitFor() == 0) { "Java version check failed: $text" }
                        text
                    }
            }.getOrElse { throw IllegalStateException("Java runtime could not launch on this Android device: ${it.message}", it) }
            check(Regex("""version "21(?:[.+-]|")""").containsMatchIn(output)) {
                "Expected Java 21 but runtime reported: $output"
            }

            val previous = File(filesDir, "java21-arm64-previous")
            if (previous.exists()) check(previous.deleteRecursively()) { "Could not clear previous Java backup." }
            val hadPrevious = root.exists()
            if (hadPrevious) check(root.renameTo(previous)) { "Could not preserve previous Java runtime." }
            try {
                check(extracted.renameTo(root)) { "Could not activate Java runtime." }
                val installedJava = File(root, java.relativeTo(extracted).invariantSeparatorsPath)
                check(installedJava.isFile && installedJava.canExecute()) { "Installed Java executable is missing or not executable." }
                if (previous.exists()) previous.deleteRecursively()
                onProgress("Java 21 Android ARM64 runtime installed and version-checked.")
                return InstallResult(installedJava, output)
            } catch (failure: Exception) {
                if (root.exists()) root.deleteRecursively()
                if (hadPrevious && previous.exists()) previous.renameTo(root)
                throw failure
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun downloadVerified(url: String, destination: File, expectedSha256: String) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CraftNode-Android")
        }
        try {
            check(connection.responseCode in 200..299) { "Java runtime download failed with HTTP ${connection.responseCode}." }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 100L * 1024L * 1024L) { "Java runtime download exceeded the 100 MB safety limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        check(sha256(destination) == expectedSha256) {
            "Java runtime SHA-256 verification failed; the downloaded file was rejected."
        }
    }

    private fun extractTarXz(archive: File, destination: File) {
        val root = destination.canonicalFile
        TarArchiveInputStream(XZCompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            var entries = 0
            while (true) {
                val entry = tar.nextTarEntry ?: break
                entries++
                check(entries <= 40_000) { "Java archive contains too many entries." }
                val output = File(root, entry.name).canonicalFile
                check(output.path == root.path || output.path.startsWith(root.path + File.separator)) {
                    "Unsafe path found in Java runtime archive."
                }
                if (entry.isSymbolicLink) {
                    val target = File(output.parentFile, entry.linkName).canonicalFile
                    check(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
                        "Unsafe symbolic link found in Java runtime archive."
                    }
                    output.parentFile?.mkdirs()
                    if (output.exists()) output.delete()
                    Os.symlink(entry.linkName, output.absolutePath)
                } else if (entry.isDirectory) {
                    check(output.mkdirs() || output.isDirectory) { "Cannot create Java runtime directory." }
                } else if (entry.isFile) {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { tar.copyTo(it) }
                    if ((entry.mode and 73) != 0) runCatching { Os.chmod(output.absolutePath, entry.mode and 511) }
                } else {
                    check(!entry.isLink) { "Hard links and special files are not supported in Java runtime archives." }
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

    companion object {
        const val ASSET_URL = "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre21/jre21-android-arm64.tar.xz"
        const val ASSET_SHA256 = "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe"
    }
}
