package app.bedrockhost.mobile

import android.content.Intent
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
import java.util.zip.ZipOutputStream
import java.util.Properties

private val ManagerPanel = Color(0xFF1D1829)
private val ManagerLine = Color(0xFF332A46)
private val ManagerText = Color(0xFFF1EDF8)
private val ManagerMuted = Color(0xFFA39AB7)
private val ManagerGreen = Color(0xFF4ADE80)

@Composable
fun WorldManagerPage(selectedProfile: String, onSelectProfile: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { CraftNodeDatabase(context) }
    val worldsRoot = remember { File(context.filesDir, "worlds") }
    var worlds by remember { mutableStateOf<List<File>>(emptyList()) }
    var newName by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("World files stay in their existing locations. No world is deleted by this screen.") }
    fun refresh() {
        worldsRoot.mkdirs()
        worlds = worldsRoot.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[A-Za-z0-9_-]{1,64}")) }?.sortedBy { it.name } ?: emptyList()
    }
    LaunchedEffect(worldsRoot) { refresh() }
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
        Text("PROFILES", color = ManagerGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(worlds, key = { it.name }) { world ->
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
                        WorldCustomisationPanel(worldDir = world, onNotice = { notice = it })
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
private fun WorldCustomisationPanel(worldDir: File, onNotice: (String) -> Unit) {
    val settingsFile = remember(worldDir.absolutePath) { File(worldDir, "craftnode-profile.properties") }
    val settings = remember(worldDir.absolutePath) {
        Properties().apply {
            if (settingsFile.isFile) runCatching { settingsFile.inputStream().use { load(it) } }
        }
    }
    var difficulty by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("difficulty", "Normal")) }
    var gameMode by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("gameMode", "Survival")) }
    var cheats by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("cheats", "false").toBoolean()) }
    var pvp by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("pvp", "true").toBoolean()) }
    var keepInventory by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("keepInventory", "false").toBoolean()) }
    var maxPlayers by remember(worldDir.absolutePath) { mutableStateOf(settings.getProperty("maxPlayers", "10")) }
    fun save(key: String, value: String) {
        settings.setProperty(key, value)
        runCatching { settingsFile.outputStream().use { settings.store(it, "CraftNode profile settings") } }
            .onSuccess { onNotice("Saved ${worldDir.name} profile settings. Engine application is a separate compatibility step.") }
            .onFailure { onNotice("Could not save profile settings: ${it.message ?: "unknown error"}") }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF15111F)), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("WORLD STUDIO", color = ManagerGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("Profile settings are saved locally. They are not yet guaranteed to change the running engine.", color = ManagerMuted, fontSize = 10.sp)
            Text("Difficulty", color = ManagerText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Peaceful", "Easy", "Normal", "Hard").forEach { option ->
                    FilterChip(selected = difficulty == option, onClick = { difficulty = option; save("difficulty", option) }, label = { Text(option, fontSize = 10.sp) })
                }
            }
            Text("Game mode", color = ManagerText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Survival", "Creative", "Adventure", "Spectator").forEach { option ->
                    FilterChip(selected = gameMode == option, onClick = { gameMode = option; save("gameMode", option) }, label = { Text(option, fontSize = 10.sp) })
                }
            }
            SettingSwitchRow("Cheats", cheats) { cheats = it; save("cheats", it.toString()) }
            SettingSwitchRow("PvP", pvp) { pvp = it; save("pvp", it.toString()) }
            SettingSwitchRow("Keep inventory", keepInventory) { keepInventory = it; save("keepInventory", it.toString()) }
            OutlinedTextField(value = maxPlayers, onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }.take(3)
                maxPlayers = filtered
                if (filtered.isNotBlank() && filtered.toIntOrNull() in 1..100) save("maxPlayers", filtered)
            }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Max players (1–100)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedButton(onClick = {
                difficulty = "Normal"; gameMode = "Survival"; cheats = false; pvp = true; keepInventory = false; maxPlayers = "10"
                settings.clear()
                settings.setProperty("difficulty", difficulty)
                settings.setProperty("gameMode", gameMode)
                settings.setProperty("cheats", cheats.toString())
                settings.setProperty("pvp", pvp.toString())
                settings.setProperty("keepInventory", keepInventory.toString())
                settings.setProperty("maxPlayers", maxPlayers)
                runCatching { settingsFile.outputStream().use { settings.store(it, "CraftNode profile settings") } }
                    .onSuccess { onNotice("Restored default profile settings for ${worldDir.name}.") }
                    .onFailure { onNotice("Could not save defaults: ${it.message ?: "unknown error"}") }
            }, modifier = Modifier.fillMaxWidth()) { Text("Reset profile settings") }
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
