package app.bedrockhost.mobile.craftmind

import java.io.File
import java.util.Properties

/** Small, app-private snapshot written by the host service and read by CraftMind. */
data class ServerRuntimeSnapshot(
    val state: String,
    val profileId: String,
    val engineId: String,
    val pid: Long,
    val updatedAt: Long
)

class ServerRuntimeStatusStore(private val file: File) {
    @Synchronized
    fun write(state: String, profileId: String, engineId: String, pid: Long = 0L) {
        require(state in ALLOWED_STATES)
        val props = Properties().apply {
            setProperty("state", state)
            setProperty("profileId", profileId.take(64))
            setProperty("engineId", engineId.take(64))
            setProperty("pid", pid.coerceAtLeast(0L).toString())
            setProperty("updatedAt", System.currentTimeMillis().toString())
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.outputStream().buffered().use { props.store(it, "CraftNode runtime status") }
        if (file.exists() && !file.delete()) {
            temp.delete()
            throw IllegalStateException("Could not replace runtime status snapshot")
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IllegalStateException("Could not publish runtime status snapshot")
        }
    }

    fun read(): ServerRuntimeSnapshot? = runCatching {
        if (!file.isFile) return null
        val props = Properties().apply { file.inputStream().buffered().use(::load) }
        val state = props.getProperty("state") ?: return null
        if (state !in ALLOWED_STATES) return null
        ServerRuntimeSnapshot(
            state = state,
            profileId = props.getProperty("profileId").orEmpty().take(64),
            engineId = props.getProperty("engineId").orEmpty().take(64),
            pid = props.getProperty("pid")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            updatedAt = props.getProperty("updatedAt")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        )
    }.getOrNull()

    companion object {
        private val ALLOWED_STATES = setOf("starting", "running", "stopping", "stopped", "failed")
    }
}
