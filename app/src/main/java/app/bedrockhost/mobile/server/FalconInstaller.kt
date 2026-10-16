package app.bedrockhost.mobile.server

import java.io.File

/**
 * Validates the Falcon executable packaged by Android into nativeLibraryDir.
 * Android extracts native libraries from the APK with executable permissions;
 * copying an ELF executable into filesDir/code_cache is denied on some devices.
 */
class FalconInstaller(private val nativeLibraryDir: File) {
    data class InstallResult(val executable: File, val version: String)

    fun install(onProgress: (String) -> Unit = {}): InstallResult {
        onProgress("Checking bundled Falcon 1.26.52 Android ARM64 engine…")
        val executable = File(nativeLibraryDir, "libfalcon.so")
        check(executable.isFile) {
            "Falcon is not bundled in this APK. Install the latest CraftNode build."
        }
        check(executable.canExecute()) {
            "Android did not mark bundled Falcon executable. Reinstall CraftNode."
        }
        onProgress("Bundled Falcon found in Android nativeLibraryDir. Device execution still needs testing.")
        return InstallResult(executable, "Falcon 1.26.52 experimental")
    }
}
