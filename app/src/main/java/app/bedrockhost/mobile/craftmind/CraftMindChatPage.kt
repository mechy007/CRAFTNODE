package app.bedrockhost.mobile.craftmind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class MindMessage(val user: Boolean, val text: String)

@Composable
fun CraftMindChatPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(
            MindMessage(false, "CraftMind bridge is connected to CraftNode's local read-only data. Try “list worlds”, “show logs”, “world survival”, “server status”, or “list players”. Live status and player parsing are not available yet.")
        )
    }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val bridge = remember(context) { CraftMindReadOnlyBridge(CraftNodeBridgeDataSource(context)) }

    fun sendMessage() {
        val question = draft.trim()
        if (question.isBlank() || busy) return
        draft = ""
        messages.add(MindMessage(true, question))
        busy = true
        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                val normalized = question.lowercase()
                val requestId = UUID.randomUUID().toString()
                val request = when {
                    normalized.contains("world") && (normalized.contains("list") || normalized.contains("all")) ->
                        CraftMindToolRequest(requestId = requestId, tool = "world.list")
                    normalized.contains("log") || normalized.contains("error") || normalized.contains("crash") ->
                        CraftMindToolRequest(requestId = requestId, tool = "server.logs.explain", arguments = mapOf("maxLines" to "40"))
                    normalized.contains("player") ->
                        CraftMindToolRequest(requestId = requestId, tool = "players.list")
                    normalized.contains("status") || normalized.contains("running") || normalized.contains("online") ->
                        CraftMindToolRequest(requestId = requestId, tool = "server.status")
                    normalized.contains("world") -> {
                        val token = Regex("[A-Za-z0-9_-]{1,64}").findAll(question).lastOrNull()?.value
                        if (token == null || token.equals("world", true)) null
                        else CraftMindToolRequest(requestId = requestId, tool = "world.get", arguments = mapOf("worldId" to token))
                    }
                    else -> null
                }
                if (request == null) {
                    "I can currently read saved world profiles, saved world settings, and recent server logs. Try “list worlds”, “world survival”, “show logs”, “server status”, or “list players”. This first chat uses local intent matching; a real AI provider is not connected yet."
                } else {
                    val result = bridge.execute(request)
                    when (result.status) {
                        CraftMindToolStatus.SUCCESS -> when (result.tool) {
                            "server.status" -> {
                                "Server runtime status (verified):\n" + result.data.entries
                                    .filter { it.key != "updatedAt" }
                                    .joinToString("\n") { (key, value) -> "• $key: $value" }
                            }
                            "world.list" -> {
                                val rows = result.data["worlds"].orEmpty()
                                if (rows.isBlank()) "No saved world profiles were found in CraftNode's app-private world folder."
                                else "Saved world profiles (ID / display name):\n" + rows.lines().joinToString("\n") { row ->
                                    row.split("\t", limit = 2).let { parts -> "• ${parts.getOrElse(0) { "" }} — ${parts.getOrElse(1) { "" }}" }
                                }
                            }
                            "world.get" -> "Saved profile settings:\n" + result.data.entries.joinToString("\n") { (key, value) -> "• $key: $value" }
                            "server.logs.explain" -> "Recent saved server log excerpt (not AI-explained yet):\n" + result.data["logExcerpt"].orEmpty().ifBlank { "No log lines were returned." }
                            else -> result.message
                        }
                        CraftMindToolStatus.UNSUPPORTED -> result.message
                        CraftMindToolStatus.REJECTED -> "I couldn't run that request safely: ${result.message}"
                        else -> "CraftNode couldn't retrieve that data: ${result.message}"
                    }
                }
            }
            messages.add(MindMessage(false, answer))
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF34D399))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("CraftMind", color = Color(0xFFE8EEF8), fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("CRAFTNODE READ-ONLY ASSISTANT", color = Color(0xFF91A0B7), fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10231F)), shape = RoundedCornerShape(12.dp)) {
            Text(
                "Safe mode: this chat can read supported local data only. It cannot run console commands or change your worlds.",
                modifier = Modifier.padding(12.dp), color = Color(0xFFB7E8D0), fontSize = 11.sp, lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
        ) {
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.user) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (message.user) Color(0xFF123B30) else Color(0xFF111622),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(if (message.user) 0.88f else 0.96f)
                    ) {
                        Text(
                            message.text,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFE8EEF8),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            if (busy) item { Text("Reading CraftNode data…", color = Color(0xFF91A0B7), fontSize = 11.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                placeholder = { Text("Ask about worlds or logs…") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = { sendMessage() },
                enabled = draft.isNotBlank() && !busy,
                modifier = Modifier.height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.Default.Send, contentDescription = "Send message") }
        }
    }
}
