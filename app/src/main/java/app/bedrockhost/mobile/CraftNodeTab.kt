package app.bedrockhost.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CraftNodeTab() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("craftnode_panel", 0) }
    var panelUrl by remember { mutableStateOf(prefs.getString("panel_url", "") ?: "") }
    var token by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf(emptyList<CraftNodeClient.Server>()) }
    var selected by remember { mutableStateOf<CraftNodeClient.Server?>(null) }
    var command by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Connect to an API-enabled panel. Your API key is not saved by this screen.") }
    val scope = rememberCoroutineScope()

    fun runAction(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() } }
            result.fold({ message = it }, { message = it.message ?: "Request failed." })
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CraftNode panel", style = MaterialTheme.typography.titleLarge)
        Text("Manage a remote hosted server here; this is separate from hosting locally on this phone.", fontSize = 13.sp)
        OutlinedTextField(panelUrl, { panelUrl = it }, Modifier.fillMaxWidth(), label = { Text("Panel HTTPS URL") }, singleLine = true)
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Client API key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && panelUrl.startsWith("https://") && token.isNotBlank(), onClick = {
                prefs.edit().putString("panel_url", panelUrl.trim().trimEnd('/')).apply()
                runAction {
                    val found = CraftNodeClient.listServers(panelUrl, token)
                    withContext(Dispatchers.Main) { servers = found; selected = found.firstOrNull() }
                    "Connected. Found ${found.size} server(s)."
                }
            }) { Text("Connect / refresh") }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Text(message, fontSize = 12.sp)
        if (servers.isEmpty()) {
            Text("Servers appear after a successful connection. Some providers disable Client API access.", fontSize = 13.sp)
        } else {
            Text("Your servers", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f, fill = true), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(servers, key = { it.identifier }) { server ->
                    Card(Modifier.fillMaxWidth().clickable { selected = server }) {
                        Column(Modifier.padding(10.dp)) {
                            Text(server.name, style = MaterialTheme.typography.titleSmall)
                            Text("${server.state} · CPU ${"%.1f".format(server.cpuPercent)}% · RAM ${server.memoryMb} MB · Disk ${server.diskMb} MB", fontSize = 12.sp)
                            if (selected?.identifier == server.identifier) Text("Selected", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        selected?.let { server ->
            Text("Controls: ${server.name}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = !busy, onClick = { runAction { CraftNodeClient.power(panelUrl, token, server.identifier, "start"); "Start request sent." } }) { Text("Start") }
                OutlinedButton(enabled = !busy, onClick = { runAction { CraftNodeClient.power(panelUrl, token, server.identifier, "restart"); "Restart request sent." } }) { Text("Restart") }
                OutlinedButton(enabled = !busy, onClick = { runAction { CraftNodeClient.power(panelUrl, token, server.identifier, "stop"); "Stop request sent." } }) { Text("Stop") }
            }
            OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Console command") }, singleLine = true)
            Button(enabled = !busy && command.isNotBlank(), onClick = {
                val cmd = command
                runAction { CraftNodeClient.command(panelUrl, token, server.identifier, cmd); "Command request sent." }
            }) { Text("Send command") }
        }
    }
}
