package app.bedrockhost.mobile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.bedrockhost.mobile.server.ServerHostService
import kotlinx.coroutines.delay
import java.io.File
import java.util.Properties

private val HostBg = Color(0xFF080D18)
private val HostPanel = Color(0xFF111B2C)
private val HostLine = Color(0xFF24334A)
private val HostGreen = Color(0xFF34D399)
private val HostMuted = Color(0xFF91A3BA)

@Composable
fun LocalHostDashboard(onOpenConsole: () -> Unit) {
    val context = LocalContext.current
    val logFile = remember { File(context.filesDir, "logs/server-console.log") }
    var logs by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("Configure your world before hosting it.") }
    var showWorldSetup by remember { mutableStateOf(false) }
    var worldName by remember { mutableStateOf("My World") }
    var gameMode by remember { mutableStateOf("Survival") }
    var difficulty by remember { mutableStateOf("Normal") }
    var seed by remember { mutableStateOf("") }
    var cheats by remember { mutableStateOf(false) }
    var setupConfirmed by remember { mutableStateOf(false) }
    var activeProfileId by remember { mutableStateOf(context.getSharedPreferences("craftnode_settings", Context.MODE_PRIVATE).getString("host_profile_id", "survival") ?: "survival") }

    fun refresh() {
        logs = runCatching {
            if (logFile.exists()) logFile.readLines().takeLast(160).joinToString("\n")
            else "No logs yet. Install the Bedrock engine in Settings, then start the server."
        }.getOrDefault("Could not read the server log.")
    }

    fun openWorldSetup() {
        val prefs = context.getSharedPreferences("craftnode_settings", Context.MODE_PRIVATE)
        worldName = prefs.getString("host_world_name", "My World") ?: "My World"
        gameMode = prefs.getString("host_game_mode", "Survival") ?: "Survival"
        difficulty = prefs.getString("host_difficulty", "Normal") ?: "Normal"
        seed = prefs.getString("host_seed", "") ?: ""
        cheats = prefs.getBoolean("host_cheats", false)
        setupConfirmed = false
        showWorldSetup = true
    }

    fun saveWorldAndHost() {
        val cleanName = worldName.trim()
        if (cleanName.isBlank() || cleanName.length > 40) {
            notice = "Enter a world name from 1 to 40 characters."
            return
        }
        val cleanSeed = seed.trim()
        if (cleanSeed.length > 64 || (cleanSeed.isNotBlank() && !cleanSeed.matches(Regex("-?[0-9]+")))) {
            notice = "Seed is optional. If provided, use a signed whole number up to 64 characters."
            return
        }
        if (!setupConfirmed) {
            notice = "Review the settings and confirm before hosting."
            return
        }
        val profileId = cleanName.lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-', '_')
            .take(40)
            .ifBlank { "world" }
        val worldDir = File(context.filesDir, "worlds/$profileId")
        val profileFile = File(worldDir, "craftnode-profile.properties")
        val serverFile = File(worldDir, "server.properties")
        runCatching {
            check(worldDir.exists() || worldDir.mkdirs()) { "Could not create the world profile." }
            val profile = Properties().apply {
                if (profileFile.isFile) profileFile.inputStream().use { load(it) }
                setProperty("worldName", cleanName)
                setProperty("gameMode", gameMode)
                setProperty("difficulty", difficulty)
                setProperty("seed", cleanSeed)
                setProperty("cheats", cheats.toString())
                setProperty("maxPlayers", getProperty("maxPlayers", "10"))
            }
            profileFile.outputStream().use { profile.store(it, "CraftNode pre-host world setup") }

            val serverProps = Properties().apply {
                if (serverFile.isFile) serverFile.inputStream().use { load(it) }
                setProperty("difficulty", when (difficulty) { "Peaceful" -> "0"; "Easy" -> "1"; "Normal" -> "2"; else -> "3" })
                setProperty("gamemode", when (gameMode) { "Creative" -> "1"; "Adventure" -> "2"; "Spectator" -> "3"; else -> "0" })
                setProperty("allow-cheats", if (cheats) "on" else "off")
                setProperty("level-seed", cleanSeed)
                setProperty("server-name", cleanName)
                if (!containsKey("max-players")) setProperty("max-players", "10")
            }
            serverFile.outputStream().use { serverProps.store(it, "CraftNode world settings; seed applies when generating a new world") }

            context.getSharedPreferences("craftnode_settings", Context.MODE_PRIVATE).edit()
                .putString("host_world_name", cleanName)
                .putString("host_profile_id", profileId)
                .putString("host_game_mode", gameMode)
                .putString("host_difficulty", difficulty)
                .putString("host_seed", cleanSeed)
                .putBoolean("host_cheats", cheats)
                .apply()
            activeProfileId = profileId
            val intent = Intent(context, ServerHostService::class.java)
                .setAction(ServerHostService.ACTION_START)
                .putExtra(ServerHostService.EXTRA_PROFILE_ID, profileId)
            ContextCompat.startForegroundService(context, intent)
            showWorldSetup = false
            notice = "World settings saved. Hosting requested; wait for the console to confirm server readiness."
        }.onFailure { notice = "Could not save world settings: ${it.message ?: "unknown error"}" }
    }

    LaunchedEffect(logFile) {
        while (true) {
            refresh()
            delay(1500)
        }
    }

    val logLines = logs.lineSequence().toList()
    val lastStartIndex = logLines.indexOfLast { line ->
        line.contains("process started") || line.contains("process launched")
    }
    val lastStopIndex = logLines.indexOfLast { line ->
        line.contains("exited with code") || line.contains("Start failed:")
    }
    val running = lastStartIndex >= 0 && lastStartIndex > lastStopIndex
    val serverReady = running && logLines.drop(lastStartIndex + 1).any { line ->
        line.contains("Server started.", ignoreCase = true) ||
            line.contains("Server started on", ignoreCase = true) ||
            line.contains("server is now listening", ignoreCase = true)
    }
    val lastState = logLines.lastOrNull { line ->
        line.contains("Start failed:") || line.contains("exited with code")
    }.orEmpty()
    val status = when {
        serverReady -> "SERVER READY"
        running -> "PROCESS STARTED · WAITING"
        lastState.contains("Start failed:") -> "START FAILED"
        logs.isBlank() -> "NOT STARTED"
        else -> "OFFLINE / UNKNOWN"
    }

    if (showWorldSetup) {
        AlertDialog(
            onDismissRequest = { showWorldSetup = false },
            title = { Text("Configure your world") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Complete this setup before CraftNode starts hosting.", color = HostMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = worldName,
                        onValueChange = { worldName = it.take(40) },
                        label = { Text("World name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("GAME MODE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    listOf("Survival", "Creative", "Adventure", "Spectator").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = gameMode == option, onClick = { gameMode = option })
                            Text(option)
                        }
                    }
                    HorizontalDivider()
                    Text("DIFFICULTY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    listOf("Peaceful", "Easy", "Normal", "Hard").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = difficulty == option, onClick = { difficulty = option })
                            Text(option)
                        }
                    }
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { seed = it.filter { c -> c.isDigit() || c == '-' }.take(64) },
                        label = { Text("World seed (optional)") },
                        placeholder = { Text("Leave blank for a random seed") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cheats, onCheckedChange = { cheats = it })
                        Column {
                            Text("Allow cheats")
                            Text("Engine support may vary.", color = HostMuted, fontSize = 11.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setupConfirmed, onCheckedChange = { setupConfirmed = it })
                        Text("I have reviewed these world settings")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { saveWorldAndHost() }, enabled = setupConfirmed) {
                    Text("Save & Host World")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWorldSetup = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().background(HostBg).padding(horizontal = 15.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CraftNode", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("BEDROCK EDITION · ON THIS ANDROID DEVICE", color = HostMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Row(
                Modifier.background(HostPanel, CircleShape).border(1.dp, HostLine, CircleShape).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(if (serverReady) HostGreen else if (running) Color(0xFFFBBF24) else Color(0xFFF87171), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status, color = if (serverReady) HostGreen else if (running) Color(0xFFFBBF24) else Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = HostPanel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HOST DETAILS", color = HostMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                val runtime = Runtime.getRuntime()
                val appHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HostInfoTile("Edition", "Bedrock", Modifier.weight(1f))
                    HostInfoTile("Protocol", "UDP · 19132", Modifier.weight(1f))
                    HostInfoTile("App heap", "${appHeapMb} MB", Modifier.weight(1f))
                }
                Text(
                    "Choose your world settings before hosting. For LAN play, connect using this phone's Wi-Fi IP and port 19132.",
                    color = HostMuted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (running) {
                                val intent = Intent(context, ServerHostService::class.java)
                                    .setAction(ServerHostService.ACTION_STOP)
                                ContextCompat.startForegroundService(context, intent)
                                notice = "Stop requested; watch the real console output."
                            } else {
                                openWorldSetup()
                            }
                        },
                        modifier = Modifier.weight(1f).height(49.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFFBE3455) else Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (running) "STOP" else "CONFIGURE & HOST", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = {
                            if (running) {
                                val intent = Intent(context, ServerHostService::class.java)
                                    .setAction(ServerHostService.ACTION_RESTART)
                                    .putExtra(ServerHostService.EXTRA_PROFILE_ID, activeProfileId)
                                ContextCompat.startForegroundService(context, intent)
                                notice = "Restart requested; check the console for the actual result."
                            } else {
                                openWorldSetup()
                            }
                        },
                        modifier = Modifier.weight(1f).height(49.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (running) "RESTART" else "SETUP", fontWeight = FontWeight.Bold)
                    }
                }
                Text(notice, color = HostGreen, fontSize = 11.sp)
            }
        }

        Card(
            Modifier.weight(1f, fill = true),
            colors = CardDefaults.cardColors(containerColor = HostPanel),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = HostGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("REAL SERVER CONSOLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenConsole) { Text("Full logs", fontSize = 11.sp) }
                }
                Surface(
                    Modifier.weight(1f).fillMaxWidth().border(1.dp, HostLine, RoundedCornerShape(10.dp)),
                    color = Color(0xFF050912), shape = RoundedCornerShape(10.dp)
                ) {
                    LazyColumn(contentPadding = PaddingValues(10.dp)) {
                        item {
                            Text(
                                logs.ifBlank { "No server output yet. Install the Bedrock engine from Settings." },
                                color = Color(0xFFB7F7CE), fontSize = 10.sp, lineHeight = 15.sp
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PocketMine console command") },
                    placeholder = { Text("help, list, time set day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        sendHostCommand(context, command) { notice = it }
                        command = ""
                    })
                )
                Button(
                    onClick = {
                        sendHostCommand(context, command) { notice = it }
                        command = ""
                    },
                    enabled = command.isNotBlank() && running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send command to server") }
            }
        }
    }
}

@Composable
private fun HostInfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color(0xFF0B1321), RoundedCornerShape(11.dp))
            .border(1.dp, HostLine, RoundedCornerShape(11.dp)).padding(11.dp)
    ) {
        Text(label, color = HostMuted, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = HostGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun sendHostCommand(context: Context, raw: String, report: (String) -> Unit) {
    val value = raw.trim().removePrefix("/")
    if (value.isBlank() || value.length > 512 || value.contains('\n') || value.contains('\r')) {
        report("Enter one valid console command.")
        return
    }
    val intent = Intent(context, ServerHostService::class.java)
        .setAction(ServerHostService.ACTION_COMMAND)
        .putExtra(ServerHostService.EXTRA_COMMAND, value)
    ContextCompat.startForegroundService(context, intent)
    report("Command request sent. Check console output for the server response.")
}
