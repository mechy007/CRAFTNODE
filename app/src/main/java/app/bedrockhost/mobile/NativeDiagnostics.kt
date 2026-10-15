package app.bedrockhost.mobile

/**
 * Optional native diagnostics used to inspect device capacity before server hosting.
 * The app remains usable if a device cannot load the native library.
 */
object NativeDiagnostics {
    private val libraryLoaded: Boolean = runCatching {
        System.loadLibrary("craftnode_native")
    }.isSuccess

    private external fun nativeReport(): String

    fun report(): String = if (libraryLoaded) {
        runCatching { nativeReport() }.getOrElse { "Native diagnostics failed: ${it.message ?: "unknown error"}" }
    } else {
        "Native C++ diagnostics library is unavailable on this device."
    }
}
