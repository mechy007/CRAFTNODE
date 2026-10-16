package app.bedrockhost.mobile.server

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** App-private profile snapshots. Stop the server before restoring a snapshot. */
class WorldBackupManager(
    private val worldsRoot: File,
    private val backupsRoot: File = File(worldsRoot.parentFile, "backups")
) {
    data class Backup(val file: File, val profileId: String, val createdAt: Long, val bytes: Long)

    fun list(profileId: String): List<Backup> {
        requireSafeProfileId(profileId)
        return profileBackupDir(profileId).listFiles { file -> file.isFile && file.extension == "zip" }
            .orEmpty().map { Backup(it, profileId, it.lastModified(), it.length()) }
            .sortedByDescending { it.createdAt }
    }

    @Synchronized fun createSnapshot(profileId: String): Backup {
        requireSafeProfileId(profileId)
        val world = File(worldsRoot, profileId)
        require(world.isDirectory) { "World profile does not exist: $profileId" }
        val files = world.walkTopDown().filter { it.isFile }.toList()
        require(files.isNotEmpty()) { "World profile is empty; refusing to create a misleading backup." }
        val dir = profileBackupDir(profileId).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val target = File(dir, "$stamp.zip")
        val temp = File(dir, ".$stamp.zip.tmp")
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                files.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(world).invariantSeparatorsPath))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            check(temp.length() > 0L) { "Backup archive is empty." }
            check(temp.renameTo(target)) { "Could not finalize backup archive." }
            return Backup(target, profileId, target.lastModified(), target.length())
        } finally { if (temp.exists()) temp.delete() }
    }

    /** Restores only into a missing profile directory, staging files before final rename. */
    @Synchronized fun restoreSnapshot(profileId: String, backup: File) {
        requireSafeProfileId(profileId)
        val allowedDir = profileBackupDir(profileId).canonicalFile
        require(backup.canonicalFile.parentFile == allowedDir) { "Backup is outside this profile's backup directory." }
        require(backup.isFile && backup.extension == "zip") { "Backup archive is missing or invalid." }
        val target = File(worldsRoot, profileId)
        require(!target.exists()) { "World profile already exists. Move it aside before restoring." }
        worldsRoot.mkdirs()
        val staging = File(worldsRoot, ".$profileId-restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            ZipInputStream(backup.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val output = File(staging, entry.name)
                    require(output.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath())) { "Backup contains an unsafe path." }
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                }
            }
            require(staging.walkTopDown().any { it.isFile }) { "Backup contains no world files." }
            check(staging.renameTo(target)) { "Could not finalize restored world." }
        } finally { if (staging.exists()) staging.deleteRecursively() }
    }

    private fun profileBackupDir(profileId: String) = File(backupsRoot, profileId)
    private fun requireSafeProfileId(profileId: String) {
        require(profileId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid profile ID." }
    }
}
