package app.bedrockhost.mobile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.bedrockhost.mobile.data.CraftNodeDatabase
import app.bedrockhost.mobile.server.ServerHostService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.Properties

private val ManagerPanel = Color(0xFF1D1829)
private val ManagerLine = Color(0xFF332A46)
private val ManagerText = Color(0xFFF1EDF8)
private val ManagerMuted = Color(0xFFA39AB7)
private val ManagerGreen = Color(0xFF4ADE80)

private data class PendingCraftNodeDelete(val file: File, val kind: String)

@Composable
fun WorldManagerPage(selectedProfile: String, onSelectProfile: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { CraftNodeDatabase(context) }
    val worldsRoot = remember { File(context.filesDir, "worlds") }
    var worlds by remember { mutableStateOf<List<File>>(emptyList()) }
    var worldSearch by remember { mutableStateOf("") }
    var recentFirst by remember { mutableStateOf(false) }
    fun refresh() {
        worldsRoot.mkdirs()
        worlds = worldsRoot.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[A-Za-z0-9_-]{1,64}")) }?.sortedBy { it.name } ?: emptyList()
    }

    var pendingExportWorld by remember { mutableStateOf<File?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    val exportWorldLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val source = pendingExportWorld
        pendingExportWorld = null
        if (uri != null && source != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output).use { zip ->
                        source.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(source).invariantSeparatorsPath
                            require(relative.isNotBlank() && !relative.startsWith("/") && !relative.split("/").any { it == ".." }) { "Unsafe archive path." }
                            zip.putNextEntry(ZipEntry(relative))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Could not open the destination file.")
            }.onSuccess { importNotice = "Exported " + source.name + " as a ZIP archive." }
                .onFailure { importNotice = "Export failed: " + (it.message ?: "unknown error") }
        }
    }
    val importWorldLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            var created: File? = null
            runCatching {
                val displayName = (uri.lastPathSegment ?: "ImportedWorld").substringAfterLast('/').substringBeforeLast('.')
                    .filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(40).ifBlank { "ImportedWorld" }
                var id = displayName
                var suffix = 2
                while (File(worldsRoot, id).exists()) { id = displayName.take(34) + "_" + suffix++ }
                val target = File(worldsRoot, id)
                check(target.mkdirs()) { "Could not create a new profile for this import." }
                created = target
                var totalBytes = 0L
                var entries = 0
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        val buffer = ByteArray(8192)
                        while (entry != null) {
                            entries++
                            require(entries <= 10000) { "Archive contains too many files." }
                            val path = entry.name.replace('\\', '/')
                            require(path.isNotBlank() && !path.startsWith("/") && !path.split("/").any { it == ".." } && !path.contains(':')) { "Unsafe archive path." }
                            val destination = File(target, path).canonicalFile
                            require(destination == target.canonicalFile || destination.path.startsWith(target.canonicalPath + File.separator)) { "Archive path escaped the profile." }
                            if (entry.isDirectory) destination.mkdirs() else {
                                destination.parentFile?.mkdirs()
                                FileOutputStream(destination).use { output ->
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        totalBytes += count
                                        require(totalBytes <= 1024L * 1024L * 1024L) { "Archive expands beyond the 1 GiB safety limit." }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: error("Could not read the selected archive.")
                check(entries > 0) { "The selected ZIP archive is empty." }
                val now = System.currentTimeMillis()
                val values = android.content.ContentValues().apply {
                    put("id", id); put("display_name", id.replace('-', ' ').replace('_', ' '))
                    put("engine_id", "selected"); put("world_path", target.absolutePath)
                    put("is_selected", 0); put("created_at", now); put("updated_at", now)
                }
                runCatching { db.writableDatabase.insertWithOnConflict("world_profiles", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) }
                importNotice = "Imported into new profile '" + target.name + "'. Select it and restart the server to use it."
                refresh()
            }.onFailure {
                created?.deleteRecursively()
                importNotice = "Import failed: " + (it.message ?: "invalid ZIP archive")
            }
        }
    }
    var newName by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("World files stay in their existing locations. Deletion requires confirmation.") }
    var pendingDelete by remember { mutableStateOf<PendingCraftNodeDelete?>(null) }
    var backupRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(worldsRoot) { refresh() }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Confirm deletion") },
            text = {
                Text(when (target.kind) {
                    "world" -> "Permanently delete world profile '${target.file.name}' and all files inside it? Existing backups will be kept."
                    "backup" -> "Delete backup '${target.file.name}'? This cannot be undone."
                    else -> "Delete '${target.file.name}'? This file may contain executable plugin code or a resource pack."
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching {
                        when (target.kind) {
                            "world" -> {
                                val id = target.file.name
                                val hostedId = context.getSharedPreferences("craftnode_settings", android.content.Context.MODE_PRIVATE).getString("host_profile_id", "survival") ?: "survival"
                                val consoleLog = File(context.filesDir, "logs/server-console.log").takeIf { it.isFile }?.readText().orEmpty()
                                val startAt = maxOf(consoleLog.lastIndexOf("process started", ignoreCase = true), consoleLog.lastIndexOf("process launched", ignoreCase = true))
                                val stopAt = maxOf(consoleLog.lastIndexOf("exited with code", ignoreCase = true), consoleLog.lastIndexOf("Start failed:", ignoreCase = true))
                                val serverAppearsRunning = startAt >= 0 && startAt > stopAt
                                require(id != selectedProfile && !(id == hostedId && serverAppearsRunning)) {
                                    "This profile is selected or its server process appears active. Stop the server and select another profile first."
                                }
                                require(target.file.canonicalFile.parentFile == worldsRoot.canonicalFile) { "Unsafe world path." }
                                require(target.file.isDirectory && target.file.name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid world profile." }
                                check(target.file.deleteRecursively()) { "Could not delete the profile." }
                                runCatching { db.writableDatabase.delete("world_profiles", "id = ?", arrayOf(id)) }
                            }
                            "backup" -> {
                                val backupRoot = File(context.filesDir, "backups/worlds").canonicalFile
                                require(target.file.canonicalFile.parentFile == backupRoot) { "Backup is outside CraftNode's backup folder." }
                                require(target.file.isFile && target.file.extension.equals("zip", true)) { "Invalid backup file." }
                                check(target.file.delete()) { "Could not delete the backup." }
                                backupRefresh++
                            }
                            else -> {
                                val parent = target.file.canonicalFile.parentFile
                                val allowed = worldsRoot.listFiles().orEmpty().any { profile ->
                                    parent == File(profile, "plugins").canonicalFile || parent == File(profile, "craftnode-addons").canonicalFile
                                }
                                require(allowed && target.file.isFile) { "File is outside an allowed add-on folder." }
                                check(target.file.delete()) { "Could not delete the add-on." }
                            }
                        }
                    }
                    if (result.isSuccess) {
                        notice = "Deleted ${target.file.name}. Other worlds and backups were left untouched."
                        refresh()
                        backupRefresh++
                    } else {
                        notice = "Delete failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    }
                    pendingDelete = null
                }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("World Manager", color = ManagerText, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Bedrock profiles and safe local snapshots", color = ManagerMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = ManagerPanel), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("CREATE PROFILE", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(value = newName, onValueChange = { newName = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(40) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Profile name (letters/numbers)") })
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val id = newName.trim()
                    if (id.isBlank()) notice = "Enter a profile name first."
                    else {
                        val target = File(worldsRoot, id)
                        if (target.exists()) notice = "That profile already exists; nothing was overwritten."
                        else if (!target.mkdirs()) notice = "Could not create the profile directory."
                        else {
                            val now = System.currentTimeMillis()
                            val values = android.content.ContentValues().apply {
                                put("id", id); put("display_name", id.replace('-', ' ').replace('_', ' '))
                                put("engine_id", "selected"); put("world_path", target.absolutePath)
                                put("is_selected", if (id == selectedProfile) 1 else 0)
                                put("created_at", now); put("updated_at", now)
                            }
                            runCatching { db.writableDatabase.insertWithOnConflict("world_profiles", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) }
                            newName = ""; notice = "Profile created. The engine creates actual world data when it starts."; refresh()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Create profile") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importWorldLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, modifier = Modifier.weight(1f)) { Text("Import ZIP") }
            OutlinedButton(onClick = {
                val selected = worlds.firstOrNull { it.name == selectedProfile }
                if (selected != null) {
                    pendingExportWorld = selected
                    exportWorldLauncher.launch(selected.name + ".zip")
                } else importNotice = "Select an existing profile before exporting."
            }, modifier = Modifier.weight(1f)) { Text("Export selected") }
        }
        importNotice?.let { Text(it, color = ManagerMuted, fontSize = 11.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PROFILES", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = { recentFirst = !recentFirst }) {
                Text(if (recentFirst) "Recently updated ↓" else "Name A–Z", color = ManagerGreen, fontSize = 11.sp)
            }
        }
        OutlinedTextField(
            value = worldSearch,
            onValueChange = { worldSearch = it.take(64) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search profiles") },
            placeholder = { Text("Find a world by name") }
        )
        Spacer(Modifier.height(6.dp))
        val matchingWorlds = worlds.filter { it.name.contains(worldSearch.trim(), ignoreCase = true) }
        val visibleWorlds = if (recentFirst) matchingWorlds.sortedByDescending { it.lastModified() } else matchingWorlds.sortedBy { it.name.lowercase() }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visibleWorlds.isEmpty()) {
                item { Text(if (worldSearch.isBlank()) "No profiles yet. Create one above." else "No profiles match your search.", color = ManagerMuted, fontSize = 12.sp) }
            }
            items(visibleWorlds, key = { it.name }) { world ->
                val bytes = world.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, ManagerLine, RoundedCornerShape(13.dp)), colors = CardDefaults.cardColors(containerColor = ManagerPanel), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = ManagerGreen)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(world.name, color = ManagerText, fontWeight = FontWeight.Bold)
                                Text("${world.listFiles()?.size ?: 0} top-level items · ${formatBytes(bytes)}", color = ManagerMuted, fontSize = 11.sp)
                            }
                            if (world.name == selectedProfile) Text("ACTIVE", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                onSelectProfile(world.name)
                                val now = System.currentTimeMillis()
                                val dbw = db.writableDatabase
                                dbw.beginTransaction()
                                try {
                                    dbw.execSQL("UPDATE world_profiles SET is_selected = 0")
                                    val cv = android.content.ContentValues().apply { put("is_selected", 1); put("updated_at", now) }
                                    val found = dbw.update("world_profiles", cv, "id = ?", arrayOf(world.name))
                                    if (found == 0) {
                                        val values = android.content.ContentValues().apply {
                                            put("id", world.name); put("display_name", world.name); put("engine_id", "selected")
                                            put("world_path", world.absolutePath); put("is_selected", 1); put("created_at", now); put("updated_at", now)
                                        }
                                        dbw.insert("world_profiles", null, values)
                                    }
                                    dbw.setTransactionSuccessful()
                                } finally { dbw.endTransaction() }
                                notice = "Selected ${world.name}. Restart the server to use a different profile."
                            }, modifier = Modifier.weight(1f)) { Text("Select") }
                            OutlinedButton(onClick = {
                                val backupDir = File(context.filesDir, "backups/worlds").apply { mkdirs() }
                                val backup = File(backupDir, "${world.name}-${System.currentTimeMillis()}.zip")
                                runCatching {
                                    ZipOutputStream(FileOutputStream(backup)).use { zip ->
                                        world.walkTopDown().filter { it.isFile }.forEach { file ->
                                            zip.putNextEntry(ZipEntry(file.relativeTo(world).invariantSeparatorsPath))
                                            FileInputStream(file).use { it.copyTo(zip) }
                                            zip.closeEntry()
                                        }
                                    }
                                }.onSuccess { notice = "Snapshot created: ${backup.name} (${formatBytes(backup.length())})." }
                                    .onFailure { backup.delete(); notice = "Backup failed: ${it.message ?: "unknown error"}" }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Backup")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        WorldCustomisationPanel(worldDir = world, onNotice = { notice = it }, onDeleteRequest = { file, kind -> pendingDelete = PendingCraftNodeDelete(file, kind) })
                        val backupDir = File(context.filesDir, "backups/worlds")
                        val profileBackups = remember(world.name, backupRefresh) {
                            backupDir.listFiles()?.filter { it.isFile && it.extension.equals("zip", true) && it.name.startsWith("${world.name}-") }?.sortedByDescending { it.lastModified() }.orEmpty()
                        }
                        if (profileBackups.isNotEmpty()) {
                            HorizontalDivider(color = ManagerLine)
                            Text("BACKUPS · ${profileBackups.size}", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            profileBackups.forEach { backup ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(backup.name, color = ManagerText, fontSize = 10.sp)
                                        Text(formatBytes(backup.length()), color = ManagerMuted, fontSize = 10.sp)
                                    }
                                    TextButton(onClick = { pendingDelete = PendingCraftNodeDelete(backup, "backup") }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { pendingDelete = PendingCraftNodeDelete(world, "world") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("DELETE WORLD / SERVER PROFILE") }
                    }
                }
            }
            item {
                val backupRoot = File(context.filesDir, "backups/worlds")
                val allBackups = remember(backupRefresh) {
                    backupRoot.listFiles()?.filter { it.isFile && it.extension.equals("zip", true) }?.sortedByDescending { it.lastModified() }.orEmpty()
                }
                Card(colors = CardDefaults.cardColors(containerColor = ManagerPanel), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("BACKUP LIBRARY · ${allBackups.size}", color = ManagerGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("Backups remain available even after a world profile is deleted.", color = ManagerMuted, fontSize = 10.sp)
                        if (allBackups.isEmpty()) Text("No backups created yet.", color = ManagerMuted, fontSize = 11.sp)
                        allBackups.forEach { backup ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(backup.name, color = ManagerText, fontSize = 10.sp)
                                    Text(formatBytes(backup.length()), color = ManagerMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = { pendingDelete = PendingCraftNodeDelete(backup, "backup") }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(notice, color = ManagerMuted, fontSize = 11.sp)
        Text("Snapshots are stored in CraftNode app storage at backups/worlds. Restore/import/export UI comes next.", color = ManagerMuted, fontSize = 10.sp)
    }
}


@Composable
private fun WorldCustomisationPanel(worldDir: File, onNotice: (String) -> Unit, onDeleteRequest: (File, String) -> Unit) {
    val context = LocalContext.current
    val settingsFile = remember(worldDir.absolutePath) { File(worldDir, "craftnode-profile.properties") }
    val serverPropertiesFile = remember(worldDir.absolutePath) { File(worldDir, "server.properties") }
    val settings = remember(worldDir.absolutePath) {
        Properties().apply {
            if (settingsFile.isFile) runCatching { settingsFile.inputStream().use { load(it) } }
            if (serverPropertiesFile.isFile) runCatching {
                serverPropertiesFile.inputStream().use { server ->
                    val engine = Properties().apply { load(server) }
                    if (!containsKey("difficulty")) setProperty("difficulty", when (engine.getProperty("difficulty", "1")) { "0" -> "Peaceful"; "2" -> "Normal"; "3" -> "Hard"; else -> "Easy" })
                    if (!containsKey("gameMode")) setProperty("gameMode", when (engine.getProperty("gamemode", "0")) { "1" -> "Creative"; "2" -> "Adventure"; "3" -> "Spectator"; else -> "Survival" })
                    if (!containsKey("maxPlayers")) setProperty("maxPlayers", engine.getProperty("max-players", "10"))
                    if (!containsKey("seed")) setProperty("seed", engine.getProperty("level-seed", ""))
                    if (!containsKey("cheats")) setProperty("cheats", engine.getProperty("allow-cheats", "off").equals("on", true).toString())
                    if (!containsKey("pvp")) setProperty("pvp", engine.getProperty("pvp", "on").equals("on", true).toString())
                }
            }
        }
    }
    var difficulty by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("difficulty", "Normal")) }
    var gameMode by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("gameMode", "Survival")) }
    var cheats by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("cheats", "false").toBoolean()) }
    var pvp by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("pvp", "true").toBoolean()) }
    var keepInventory by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("keepInventory", "false").toBoolean()) }
    var mobSpawning by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("mobSpawning", "true").toBoolean()) }
    var mobGriefing by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("mobGriefing", "true").toBoolean()) }
    var daylightCycle by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("daylightCycle", "true").toBoolean()) }
    var weatherCycle by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("weatherCycle", "true").toBoolean()) }
    var fireSpread by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("fireSpread", "true").toBoolean()) }
    var showCoordinates by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("showCoordinates", "true").toBoolean()) }
    var maxPlayers by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("maxPlayers", "10")) }
    var seed by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("seed", "")) }
    var importedPacks by remember(worldDir.absolutePath) { mutableIntStateOf(0) }
    val stagedDir = remember(worldDir.absolutePath) { File(worldDir, "craftnode-addons").apply { mkdirs() } }
    val pluginsDir = remember(worldDir.absolutePath) { File(worldDir, "plugins").apply { mkdirs() } }
    val addonPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        var installed = 0
        var staged = 0
        var rejected = 0
        uris.forEach { uri ->
            runCatching {
                val displayName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "addon"
                val safeName = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
                val ext = safeName.substringAfterLast('.', "").lowercase()
                when (ext) {
                    "phar" -> {
                        // PocketMine-style plugins are executable code; importing never means the plugin is trusted.
                        val out = File(pluginsDir, safeName)
                        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                            ?: error("Could not read selected file")
                        installed++
                    }
                    "mcpack", "mcaddon" -> {
                        // Stage only: community PHP engines do not automatically load Bedrock packs.
                        val out = File(stagedDir, safeName)
                        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                            ?: error("Could not read selected file")
                        staged++
                    }
                    else -> rejected++
                }
            }.onFailure { rejected++ }
        }
        importedPacks++
        onNotice("Imported $installed PocketMine plugin(s) to plugins/, staged $staged Bedrock pack(s) for manual compatibility review, rejected $rejected file(s). Review plugin trust before restarting.")
    }
    fun saveAll(reset: Boolean = false) {
        val chosenDifficulty = if (reset) "Normal" else difficulty
        val chosenMode = if (reset) "Survival" else gameMode
        val chosenCheats = if (reset) false else cheats
        val chosenPvp = if (reset) true else pvp
        val chosenKeep = if (reset) false else keepInventory
        val chosenMobSpawning = if (reset) true else mobSpawning
        val chosenMobGriefing = if (reset) true else mobGriefing
        val chosenDaylightCycle = if (reset) true else daylightCycle
        val chosenWeatherCycle = if (reset) true else weatherCycle
        val chosenFireSpread = if (reset) true else fireSpread
        val chosenCoordinates = if (reset) true else showCoordinates
        val chosenMax = if (reset) "10" else maxPlayers
        val chosenSeed = if (reset) "" else seed.trim()
        if (chosenMax.toIntOrNull()?.let { it in 1..100 } != true) {
            onNotice("Max players must be between 1 and 100.")
            return
        }
        if (chosenSeed.length > 64 || (chosenSeed.isNotBlank() && !chosenSeed.matches(Regex("-?[0-9]+")))) {
            onNotice("Seed must be blank or a signed whole number (up to 64 characters).")
            return
        }
        val snapshot = Properties().apply {
            setProperty("difficulty", chosenDifficulty)
            setProperty("gameMode", chosenMode)
            setProperty("cheats", chosenCheats.toString())
            setProperty("pvp", chosenPvp.toString())
            setProperty("keepInventory", chosenKeep.toString())
            setProperty("mobSpawning", chosenMobSpawning.toString())
            setProperty("mobGriefing", chosenMobGriefing.toString())
            setProperty("daylightCycle", chosenDaylightCycle.toString())
            setProperty("weatherCycle", chosenWeatherCycle.toString())
            setProperty("fireSpread", chosenFireSpread.toString())
            setProperty("showCoordinates", chosenCoordinates.toString())
            setProperty("maxPlayers", chosenMax)
            setProperty("seed", chosenSeed)
        }
        runCatching {
            settingsFile.outputStream().use { snapshot.store(it, "CraftNode World Studio profile settings") }
            // Write standard properties consumed by compatible PocketMine-style engines on next launch.
            val engineProps = Properties()
            if (serverPropertiesFile.isFile) serverPropertiesFile.inputStream().use { engineProps.load(it) }
            engineProps.setProperty("difficulty", when (chosenDifficulty) { "Peaceful" -> "0"; "Easy" -> "1"; "Normal" -> "2"; else -> "3" })
            engineProps.setProperty("gamemode", when (chosenMode) { "Creative" -> "1"; "Adventure" -> "2"; "Spectator" -> "3"; else -> "0" })
            engineProps.setProperty("max-players", chosenMax)
            engineProps.setProperty("allow-cheats", if (chosenCheats) "on" else "off")
            engineProps.setProperty("pvp", if (chosenPvp) "on" else "off")
            engineProps.setProperty("level-seed", chosenSeed)
            serverPropertiesFile.outputStream().use { engineProps.store(it, "CraftNode World Studio - compatible engines only") }
            difficulty = chosenDifficulty; gameMode = chosenMode; cheats = chosenCheats
            pvp = chosenPvp; keepInventory = chosenKeep; maxPlayers = chosenMax; seed = chosenSeed
            mobSpawning = chosenMobSpawning; mobGriefing = chosenMobGriefing
            daylightCycle = chosenDaylightCycle; weatherCycle = chosenWeatherCycle
            fireSpread = chosenFireSpread; showCoordinates = chosenCoordinates
            onNotice("World settings saved for ${worldDir.name}. Restart the server to load compatible server.properties. Seed affects new worlds only; Keep Inventory is saved as a preset and must be applied with a supported gamerule.")
        }.onFailure { onNotice("Could not save world settings: ${it.message ?: "unknown error"}") }
    }
    val pluginCount = pluginsDir.listFiles()?.count { it.isFile && it.extension.equals("phar", true) } ?: 0
    val stagedCount = stagedDir.listFiles()?.count { it.isFile } ?: 0
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15111F)), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("WORLD STUDIO", color = ManagerGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("Settings persist per profile. Compatible server.properties are updated; restart to apply. Engine support varies.", color = ManagerMuted, fontSize = 10.sp)
            Text("Difficulty", color = ManagerText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Peaceful", "Easy", "Normal", "Hard").forEach { option ->
                    FilterChip(selected = difficulty == option, onClick = { difficulty = option }, label = { Text(option, fontSize = 10.sp) })
                }
            }
            Text("Game mode", color = ManagerText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Survival", "Creative", "Adventure", "Spectator").forEach { option ->
                    FilterChip(selected = gameMode == option, onClick = { gameMode = option }, label = { Text(option, fontSize = 10.sp) })
                }
            }
            SettingSwitchRow("Cheats / commands", cheats) { cheats = it }
            SettingSwitchRow("PvP", pvp) { pvp = it }
            SettingSwitchRow("Keep inventory preset", keepInventory) { keepInventory = it }
            Text("ADVANCED WORLD RULE PRESETS", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
            SettingSwitchRow("Mob spawning", mobSpawning) { mobSpawning = it }
            SettingSwitchRow("Mob griefing", mobGriefing) { mobGriefing = it }
            SettingSwitchRow("Daylight cycle", daylightCycle) { daylightCycle = it }
            SettingSwitchRow("Weather cycle", weatherCycle) { weatherCycle = it }
            SettingSwitchRow("Fire spread", fireSpread) { fireSpread = it }
            SettingSwitchRow("Show coordinates preset", showCoordinates) { showCoordinates = it }
            Text("These rule presets are saved per profile. They are not automatically applied as gamerules unless the selected engine supports that integration.", color = ManagerMuted, fontSize = 10.sp)
            OutlinedTextField(value = seed, onValueChange = { seed = it.filter { c -> c.isDigit() || c == '-' }.take(64) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("World seed (blank = engine default)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = maxPlayers, onValueChange = { maxPlayers = it.filter { c -> c.isDigit() }.take(3) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Max players (1–100)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedButton(onClick = { saveAll() }, modifier = Modifier.fillMaxWidth()) { Text("SAVE WORLD SETTINGS") }
            OutlinedButton(onClick = { saveAll(reset = true) }, modifier = Modifier.fillMaxWidth()) { Text("Reset profile settings") }
            androidx.compose.material3.HorizontalDivider()
            Text("ADD-ON & PLUGIN MANAGER", color = ManagerGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("PocketMine-style .phar plugins are copied to plugins/ (review trust first). .mcpack/.mcaddon files are staged only because this engine does not guarantee Bedrock-pack support.", color = ManagerMuted, fontSize = 10.sp)
            Text("Installed plugin files: $pluginCount · Staged packs: $stagedCount", color = ManagerText, fontSize = 11.sp)
            OutlinedButton(onClick = { addonPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("IMPORT ADD-ON / PLUGIN FILES") }
            (pluginsDir.listFiles()?.filter { it.isFile }.orEmpty() + stagedDir.listFiles()?.filter { it.isFile }.orEmpty()).forEach { addon ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(addon.name, color = ManagerText, fontSize = 11.sp)
                        Text(if (addon.parentFile == pluginsDir) "Plugin · plugins/" else "Staged pack · manual compatibility review", color = ManagerMuted, fontSize = 9.sp)
                    }
                    TextButton(onClick = { onDeleteRequest(addon, "addon") }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (importedPacks > 0) Text("Import scan completed. Verify engine logs after restart.", color = ManagerMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ManagerText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PlayerManagerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var playerName by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("Commands are sent to the server. Confirm results in Console; no fake online-player list is shown.") }
    var action by remember { mutableStateOf("list") }
    val actions = listOf("list", "op", "deop", "whitelist add", "whitelist remove", "kick", "ban", "pardon")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Player Manager", color = ManagerText, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Server command controls", color = ManagerMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = ManagerPanel), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp)) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = ManagerGreen)
                Text("PLAYER COMMAND", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
                actions.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { action = option }.padding(vertical = 2.dp)) {
                        RadioButton(selected = action == option, onClick = { action = option })
                        Text(option, color = ManagerText, fontSize = 13.sp)
                    }
                }
                OutlinedTextField(value = playerName, onValueChange = { playerName = it.take(32) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Player name (not needed for list)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val name = playerName.trim()
                    if (action != "list" && !name.matches(Regex("[A-Za-z0-9_]{1,32}"))) notice = "Enter a valid player name using letters, numbers or underscore."
                    else {
                        val command = if (action == "list") "list" else "$action $name"
                        val intent = Intent(context, ServerHostService::class.java).setAction(ServerHostService.ACTION_COMMAND).putExtra(ServerHostService.EXTRA_COMMAND, command)
                        ContextCompat.startForegroundService(context, intent)
                        notice = "Sent '$command'. Check Console for the server response; commands may vary by engine."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Send to server") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(notice, color = ManagerMuted, fontSize = 11.sp)
        Text("Online players and permissions will be displayed as confirmed only after engine-specific responses are parsed.", color = ManagerMuted, fontSize = 10.sp)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
