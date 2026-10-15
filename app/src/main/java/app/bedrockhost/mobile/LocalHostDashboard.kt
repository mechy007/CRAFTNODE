package app.bedrockhost.mobile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
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
    var notice by remember { mutableStateOf("Local Bedrock hosting · real process logs") }

    fun refresh() {
        logs = runCatching {
            if (logFile.exists()) logFile.readLines().takeLast(160).joinToString("\n")
            else "No logs yet. Install the Bedrock engine in Settings, then start the server."
        }.getOrDefault("Could not read the server log.")
    }

    LaunchedEffect(logFile) {
        while (true) {
            refresh()
            delay(1500)
        }
    }

    val lastState = logs.lineSequence().lastOrNull { line ->
        line.contains("process started") || line.contains("exited with code") ||
            line.contains("Start failed:") || line.contains("no active server process")
    }.orEmpty()
    val running = lastState.contains("process started") && !lastState.contains("exited with code")
    val status = when {
        running -> "PROCESS RUNNING"
        lastState.contains("Start failed:") -> "START FAILED"
        logs.isBlank() -> "NOT STARTED"
        else -> "OFFLINE / UNKNOWN"
    }

    Column(
        Modifier.fillMaxSize().background(HostBg).padding(horizontal = 15.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MineHost Pocket", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("BEDROCK EDITION · ON THIS ANDROID DEVICE", color = HostMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Row(
                Modifier.background(HostPanel, CircleShape).border(1.dp, HostLine, CircleShape).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(if (running) HostGreen else Color(0xFFF87171), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status, color = if (running) HostGreen else Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = HostPanel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HOST DETAILS", color = HostMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HostInfoTile("Edition", "Bedrock", Modifier.weight(1f))
                    HostInfoTile("Protocol", "UDP · 19132", Modifier.weight(1f))
                }
                Text(
                    "To connect from another device, use this phone's Wi-Fi IP address and port 19132. 127.0.0.1 only works on this phone.",
                    color = HostMuted, fontSize = 11.sp, lineHeight = 16.sp
                )
                Button(
                    onClick = {
                        val intent = Intent(context, ServerHostService::class.java)
                            .setAction(if (running) ServerHostService.ACTION_STOP else ServerHostService.ACTION_START)
                            .putExtra(ServerHostService.EXTRA_PROFILE_ID, "survival")
                        ContextCompat.startForegroundService(context, intent)
                        notice = if (running) "Stop requested; watch the real console output."
                            else "Start requested; wait for real process logs before assuming it is online."
                        refresh()
                    },
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFFBE3455) else Color(0xFF059669)
                    )
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "STOP SERVER" else "START SERVER", fontWeight = FontWeight.Black)
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
