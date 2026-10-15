package app.bedrockhost.mobile.server

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * App-private world directories survive app process death and device restarts.
 * Uninstalling CraftNode removes this data, so users should export backups.
 */
class WorldStorage(context: Context) {
    private val root = File(context.filesDir, "worlds").apply { mkdirs() }
    private val backups = File(context.filesDir, "backups").apply { mkdirs() }

    fun worldDirectory(profileId: String): File {
        val safeId = profileId.trim()
        require(safeId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid world profile ID." }
        return File(root, safeId).canonicalFile.also {
            require(it.parentFile == root.canonicalFile) { "World path escaped app storage." }
            if (!it.exists()) it.mkdirs()
        }
    }

    fun listWorlds(): List<File> = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()

    fun createBackup(profileId: String): File {
        val world = worldDirectory(profileId)
        require(world.listFiles()?.isNotEmpty() == true) { "World is empty; nothing to back up yet." }
        val destination = File(backups, "${profileId}-${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            world.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(world).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return destination
    }

    /** Restore only into an empty world folder; reject zip-slip paths and oversized entry counts. */
    fun restoreBackup(profileId: String, archive: File) {
        require(archive.isFile && archive.extension.equals("zip", ignoreCase = true)) { "Select a .zip backup." }
        val target = worldDirectory(profileId)
        require(target.listFiles().isNullOrEmpty()) { "Restore requires an empty world folder." }
        var entries = 0
        ZipInputStream(FileInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= 20_000) { "Backup contains too many files." }
                val out = File(target, entry.name).canonicalFile
                require(out.toPath().startsWith(target.canonicalFile.toPath())) { "Invalid backup path." }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { stream -> zip.copyTo(stream) }
                }
                zip.closeEntry()
            }
        }
    }

    fun backupDirectory(): File = backups
}
