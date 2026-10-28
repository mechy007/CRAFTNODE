package app.bedrockhost.mobile.server

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks for a Java 21+ ARM64 runtime installed inside CraftNode's private files directory.
 * This probe does not download a runtime, run a server, or claim Android compatibility
 * until the runtime successfully executes on the target device.
 */
object PowerNukkitXRuntimeProbe {
    data class Result(
        val available: Boolean,
        val javaExecutable: String?,
        val javaVersion: String?,
        val message: String
    )

    fun inspect(context: Context): Result {
        if (Build.SUPPORTED_ABIS.none { it.equals("arm64-v8a", ignoreCase = true) }) {
            return Result(false, null, null, "PowerNukkitX runtime probe requires a 64-bit ARM Android device.")
        }

        val runtimeDir = File(context.filesDir, "pnx-runtime")
        val java = File(runtimeDir, "bin/java")
        if (!java.isFile || !java.canExecute()) {
            return Result(
                false,
                java.absolutePath,
                null,
                "No executable Java runtime is installed in CraftNode's private pnx-runtime directory."
            )
        }

        return try {
            val process = ProcessBuilder(java.absolutePath, "-version")
                .directory(runtimeDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["JAVA_HOME"] = runtimeDir.absolutePath
                    environment()["PATH"] = File(runtimeDir, "bin").absolutePath + ":" +
                        (environment()["PATH"] ?: "")
                    environment()["LD_LIBRARY_PATH"] = listOf(
                        File(runtimeDir, "lib").absolutePath,
                        File(runtimeDir, "lib/server").absolutePath,
                        environment()["LD_LIBRARY_PATH"].orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(File.pathSeparator)
                }
                .start()

            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(false, java.absolutePath, null, "Java version probe timed out.")
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val versionMatch = Regex("""version\s+"(\d+)(?:\.(\d+))?""").find(output)
            val major = versionMatch?.groupValues?.get(1)?.toIntOrNull()
            val version = versionMatch?.groupValues?.get(0)?.removePrefix("version ")?.trim('"')
            when {
                process.exitValue() != 0 ->
                    Result(false, java.absolutePath, version, "Java runtime failed its version check: ${output.take(240)}")
                major == null ->
                    Result(false, java.absolutePath, version, "Java ran but its version could not be identified.")
                major < 21 ->
                    Result(false, java.absolutePath, version, "Java $major detected; PowerNukkitX requires Java 21 or newer.")
                else ->
                    Result(true, java.absolutePath, version, "Java $version executed successfully. PowerNukkitX server compatibility is still unverified.")
            }
        } catch (error: Exception) {
            Result(false, java.absolutePath, null, "Java runtime could not execute: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
