package app.bedrockhost.mobile

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.bedrockhost.mobile.server.ServerHostService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF14101C)
private val Panel = Color(0xFF1D1829)
private val Line = Color(0xFF332A46)
private val TextMain = Color(0xFFF1EDF8)
private val Muted = Color(0xFFA39AB7)
private val Purple = Color(0xFF7C5CFF)
private val Green = Color(0xFF4ADE80)
private val TileBlue = Color(0xFF3A6EA5)
private val TileOrange = Color(0xFFA5703A)
private val TilePink = Color(0xFFC05CFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Purple,
                secondary = Green,
                background = Background,
                surface = Panel,
                onPrimary = Color.White,
                onBackground = TextMain,
                onSurface = TextMain
            )) {
                CraftNodeApp()
            }
        }
    }
}

private enum class Page(val label: String) {
    Play("Home"), Host("Host"), Remote("Remote"), Console("Console"), Settings("Settings")
}

@Composable
private fun CraftNodeApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.Play) }
    var selectedProfile by remember { mutableStateOf("Survival World") }
    var notice by remember { mutableStateOf("Ready. Install an engine in Settings before starting.") }
    val serverLogFile = remember { File(context.filesDir, "logs/server-console.log") }
    var serverRunning by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(serverLogFile) {
        while (true) {
            val relevant = runCatching {
                serverLogFile.readLines().lastOrNull { line ->
                    line.contains("process started") || line.contains("process launched") ||
                        line.contains("exited with code") || line.contains("Start failed:")
                }.orEmpty()
            }.getOrDefault("")
            serverRunning = (relevant.contains("process started") || relevant.contains("process launched")) &&
                !relevant.contains("exited with code") && !relevant.contains("Start failed:")
            kotlinx.coroutines.delay(1200)
        }
    }
    val settings = remember { context.getSharedPreferences("craftnode_settings", 0) }
    var selectedEngine by remember {
        mutableStateOf(settings.getString("selected_engine", "PocketMine-MP") ?: "PocketMine-MP")
    }
    var selectedMinecraftVersion by remember {
        mutableStateOf(settings.getString("selected_minecraft_version", "1.26.51") ?: "1.26.51")
    }

    Surface(Modifier.fillMaxSize(), color = Background) {
        Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF211832), Background, Background))
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.craftnode_mark),
                    contentDescription = "CraftNode logo",
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                )
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Craft", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Node", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Text("MINECRAFT SERVER LAUNCHER", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.15.sp)
                }
                Box(
                    Modifier.clip(CircleShape).background(Panel).border(1.dp, Line, CircleShape).padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text("BEDROCK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(Modifier.weight(1f)) {
            when (page) {
                Page.Play -> PlayPage(
                    selectedProfile = selectedProfile,
                    onSelectProfile = { selectedProfile = it; notice = "$it selected." },
                    notice = notice,
                    serverRunning = serverRunning,
                    onStart = {
                        if (serverRunning) {
                            val stopIntent = Intent(context, ServerHostService::class.java)
                                .setAction(ServerHostService.ACTION_STOP)
                            ContextCompat.startForegroundService(context, stopIntent)
                            notice = "Stop requested. Confirm shutdown in Console."
                        } else {
                        val profileId = when (selectedProfile) {
                            "Friends SMP" -> "friends"
                            "Creative Test" -> "creative"
                            else -> "survival"
                        }
                        val intent = Intent(context, ServerHostService::class.java)
                            .setAction(ServerHostService.ACTION_START)
                            .putExtra(ServerHostService.EXTRA_PROFILE_ID, profileId)
                            .putExtra(ServerHostService.EXTRA_ENGINE_ID, if (selectedEngine == "Falcon (experimental)") ServerHostService.ENGINE_FALCON else ServerHostService.ENGINE_NOVAMINE)
                        ContextCompat.startForegroundService(context, intent)
                        val engineLabel = if (selectedEngine == "Falcon (experimental)") "Falcon experimental" else "PocketMine-MP"
                        notice = "Start requested for $engineLabel. Confirm readiness in Console."
                        }
                    },
                    onOpen = { page = it }
                )
                Page.Host -> LocalHostDashboard(onOpenConsole = { page = Page.Console })
                Page.Remote -> RemoteAccessPage()
                Page.Settings -> SettingsPage(
                    nativeReport = NativeDiagnostics.report(),
                    selectedEngine = selectedEngine,
                    onSelectEngine = {
                        selectedEngine = it
                        settings.edit().putString("selected_engine", it).apply()
                    },
                    selectedMinecraftVersion = selectedMinecraftVersion,
                    onSelectMinecraftVersion = {
                        selectedMinecraftVersion = it
                        settings.edit().putString("selected_minecraft_version", it).apply()
                    },
                    onInstallEngine = {
                        val installFalcon = selectedEngine == "Falcon (experimental)"
                        val intent = Intent(context, ServerHostService::class.java)
                            .setAction(if (installFalcon) ServerHostService.ACTION_INSTALL_FALCON else ServerHostService.ACTION_INSTALL)
                        ContextCompat.startForegroundService(context, intent)
                        notice = if (installFalcon) "Downloading and verifying experimental Falcon Android ARM64 engine." else "Installing verified NovaMine and Android ARM64 PHP runtime."
                    },
                )
                Page.Console -> ConsolePage(
                    logFile = File(context.filesDir, "logs/server-console.log"),
                    onBack = { page = Page.Play },
                    onStop = {
                        val intent = Intent(context, ServerHostService::class.java)
                            .setAction(ServerHostService.ACTION_STOP)
                        ContextCompat.startForegroundService(context, intent)
                    }
                )
            }
            }

            NavigationBar(
                containerColor = Panel,
                contentColor = TextMain,
                windowInsets = WindowInsets.navigationBars
            ) {
                listOf(Page.Play, Page.Host, Page.Remote, Page.Console, Page.Settings).forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = {
                            Icon(
                                when (item) {
                                    Page.Play -> Icons.Default.Home
                                    Page.Host -> Icons.Default.Gamepad
                                    Page.Remote -> Icons.Default.Cloud
                                    Page.Console -> Icons.Default.Terminal
                                    Page.Settings -> Icons.Default.Settings
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = Purple,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    }
}


@Composable
private fun RemoteAccessPage() {
    val context = LocalContext.current
    var relayHost by remember { mutableStateOf("") }
    var controlPort by remember { mutableStateOf("7000") }
    var authToken by remember { mutableStateOf("") }
    var publicPort by remember { mutableStateOf("19132") }
    var localPort by remember { mutableStateOf("19132") }
    var notice by remember { mutableStateOf("A public FRP relay is required. CraftNode does not host that relay for you.") }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Remote access", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Let friends join through Minecraft Bedrock without installing a VPN.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("PUBLIC UDP TUNNEL", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Relay details required", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "You need an FRP server reachable from the internet with UDP forwarding enabled, a token, and an allocated public UDP port. The relay is separate from CraftNode and may cost money. Never share the relay token with friends.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Relay hostname or IPv4", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = relayHost,
            onValueChange = { relayHost = it.trim().take(253); notice = "" },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("relay.example.net") }
        )
        Spacer(Modifier.height(8.dp))
        Text("FRP control port", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = controlPort,
            onValueChange = { controlPort = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("7000") }
        )
        Spacer(Modifier.height(8.dp))
        Text("Relay token (private)", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = authToken,
            onValueChange = { authToken = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }.take(256) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            placeholder = { Text("Paste relay token") }
        )
        Spacer(Modifier.height(8.dp))
        Text("Public UDP port assigned by relay", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = publicPort,
            onValueChange = { publicPort = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("19132") }
        )
        Spacer(Modifier.height(8.dp))
        Text("Local Bedrock UDP port", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = localPort,
            onValueChange = { localPort = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("19132") }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val configResult = runCatching {
                    app.bedrockhost.mobile.multiplayer.FrpUdpTunnelConfig(
                        relayHost = relayHost,
                        relayControlPort = controlPort.toInt(),
                        authToken = authToken,
                        publicUdpPort = publicPort.toInt(),
                        localUdpPort = localPort.toInt()
                    )
                }
                configResult.onSuccess { config ->
                    val serviceIntent = Intent(context, app.bedrockhost.mobile.multiplayer.FrpTunnelService::class.java)
                        .setAction(app.bedrockhost.mobile.multiplayer.FrpTunnelService.ACTION_START)
                        .putExtra(app.bedrockhost.mobile.multiplayer.FrpTunnelService.EXTRA_RELAY_HOST, config.relayHost)
                        .putExtra(app.bedrockhost.mobile.multiplayer.FrpTunnelService.EXTRA_CONTROL_PORT, config.relayControlPort)
                        .putExtra(app.bedrockhost.mobile.multiplayer.FrpTunnelService.EXTRA_AUTH_TOKEN, config.authToken)
                        .putExtra(app.bedrockhost.mobile.multiplayer.FrpTunnelService.EXTRA_PUBLIC_UDP_PORT, config.publicUdpPort)
                        .putExtra(app.bedrockhost.mobile.multiplayer.FrpTunnelService.EXTRA_LOCAL_UDP_PORT, config.localUdpPort)
                    runCatching { androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent) }
                        .onSuccess { notice = "Tunnel start requested. Check the CraftNode notification. The public address is not verified until a friend successfully joins." }
                        .onFailure { notice = "Could not start tunnel service: ${it.message ?: "unknown error"}" }
                }.onFailure { notice = it.message ?: "Invalid relay configuration." }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) { Text("START REMOTE TUNNEL") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val serviceIntent = Intent(context, app.bedrockhost.mobile.multiplayer.FrpTunnelService::class.java)
                    .setAction(app.bedrockhost.mobile.multiplayer.FrpTunnelService.ACTION_STOP)
                runCatching { context.startService(serviceIntent) }
                    .onSuccess { notice = "Tunnel stop requested." }
                    .onFailure { notice = "Could not stop tunnel: ${it.message ?: "unknown error"}" }
                authToken = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("STOP REMOTE TUNNEL") }
        Spacer(Modifier.height(10.dp))
        Text(notice, color = if (notice.startsWith("Invalid") || notice.startsWith("Could not")) Color(0xFFFFC76A) else Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Once the tunnel process is connected, give friends the relay hostname and public UDP port in Minecraft → Servers → Add Server. CraftNode currently cannot verify a Bedrock handshake, so process startup alone does not mean joining works.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Security note: the current engine configuration disables Xbox identity verification for compatibility. Only share access with trusted players.",
            color = Color(0xFFFFC76A), fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

@Composable
private fun PlayPage(
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    notice: String,
    serverRunning: Boolean,
    onStart: () -> Unit,
    onOpen: (Page) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("SERVER STATUS", if (serverRunning) "PROCESS RUNNING" else "OFFLINE", if (serverRunning) Green else Muted)
                StatusPill("PLAYERS", "NOT REPORTED", Muted)
            }
        }
        item {
            HeroCard(selectedProfile, onSelectProfile, onStart, notice, serverRunning)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("QUICK ACCESS", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                Spacer(Modifier.weight(1f))
                Text("SERVER TOOLS", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard(Icons.Default.Terminal, "Console", "Real server logs", Purple, Modifier.weight(1f)) { onOpen(Page.Console) }
                ToolCard(Icons.Default.Cloud, "Remote access", "Private VPN setup", TileBlue, Modifier.weight(1f)) { onOpen(Page.Remote) }
            }
        }
    }
}

@Composable
private fun HeroCard(
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    onStart: () -> Unit,
    notice: String,
    serverRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF49366D), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Box {
            Canvas(Modifier.fillMaxWidth().height(230.dp)) {
                val tile = 32.dp.toPx()
                val colors = listOf(Color(0xFF352653), Color(0xFF29203D))
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = 0f
                    var col = 0
                    while (x < size.width) {
                        drawRect(colors[(row + col) % 2], androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(tile, tile))
                        x += tile
                        col++
                    }
                    y += tile
                    row++
                }
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Background.copy(alpha = 0.98f), Background.copy(alpha = 0.76f), Background.copy(alpha = 0.25f))),
                    size = size
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Panel.copy(alpha = 0.95f))),
                    size = size
                )
            }
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                    Text("  LOCAL SERVER", color = Green, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(26.dp))
                Text(selectedProfile.uppercase(), color = TextMain, fontSize = 31.sp, fontWeight = FontWeight.Black, lineHeight = 35.sp)
                Text("Your world. Your server. Your rules.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(17.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val next = when (selectedProfile) {
                                "Survival World" -> "Friends SMP"
                                "Friends SMP" -> "Creative Test"
                                else -> "Survival World"
                            }
                            onSelectProfile(next)
                        },
                        shape = RoundedCornerShape(11.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Select profile", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (serverRunning) Color(0xFFB93855) else Purple, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Gamepad, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text(if (serverRunning) "STOP SERVER" else "START SERVER", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("ENGINE: SELECTED IN SETTINGS", color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp)
                Spacer(Modifier.height(8.dp))
                Text(notice, color = Green, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.clip(CircleShape).background(Panel).border(1.dp, Line, CircleShape).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label  ", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(112.dp).clickable(onClick = onClick).border(1.dp, Line, RoundedCornerShape(15.dp)),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Column {
                Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ListPage(
    title: String,
    subtitle: String,
    entries: List<Pair<String, String>>,
    onPick: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(title, color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        entries.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onPick(entry.first) }.border(1.dp, Line, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF302448)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Purple)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(entry.first, color = TextMain, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.second, color = Muted, fontSize = 11.sp)
                    }
                    Text("›", color = Green, fontSize = 28.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Select Settings → Install Bedrock Engine before starting a server.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsPage(nativeReport: String, selectedEngine: String, onSelectEngine: (String) -> Unit, selectedMinecraftVersion: String, onSelectMinecraftVersion: (String) -> Unit, onInstallEngine: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Settings", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Customize your CraftNode experience.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Text("Minecraft version", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Choose the client version your server should accept.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple("1.26.51", "NovaMine · protocol 2193 · Android ARM64 ready", true),
            Triple("1.26.52", "Falcon · protocol 2193 · experimental Android ARM64 build", true)
        ).forEach { version ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .border(1.dp, if (selectedMinecraftVersion == version.first) Purple else Line, RoundedCornerShape(13.dp))
                    .clickable { onSelectMinecraftVersion(version.first) },
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minecraft Bedrock ${version.first}", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(version.second, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Text(if (selectedMinecraftVersion == version.first) "SELECTED" else if (version.third) "READY" else "PLANNED",
                        color = if (selectedMinecraftVersion == version.first) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (selectedMinecraftVersion == "1.26.52") {
            Text("Falcon Android ARM64 is experimental. Startup, networking, and gameplay must be tested on a real device. Keep NovaMine and your worlds untouched until verified.", color = Color(0xFFFFC76A), fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
        }
        Text("Server software", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple("PocketMine-MP", "NovaMine · Minecraft 1.26.51 · stable choice", true),
            Triple("Falcon (experimental)", "Native Android ARM64 · Minecraft 1.26.52 · testing required", true)
        ).forEach { engine ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .border(1.dp, if (selectedEngine == engine.first) Purple else Line, RoundedCornerShape(13.dp))
                    .clickable { onSelectEngine(engine.first) },
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(engine.first, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(engine.second, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Text(if (selectedEngine == engine.first) "SELECTED" else if (engine.third) "READY" else "PLANNED",
                        color = if (selectedEngine == engine.first) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Button(
            onClick = onInstallEngine,
            enabled = (selectedEngine == "PocketMine-MP" && selectedMinecraftVersion == "1.26.51") ||
                (selectedEngine == "Falcon (experimental)" && selectedMinecraftVersion == "1.26.52"),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White, disabledContainerColor = Line, disabledContentColor = Muted)
        ) {
            Text(if (selectedEngine == "Falcon (experimental)") "INSTALL EXPERIMENTAL FALCON 1.26.52" else if (selectedMinecraftVersion == "1.26.51") "INSTALL NOVAMINE 1.26.51 ENGINE" else "SELECT FALCON FOR 1.26.52", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        }
        Text("Falcon is an experimental native Android build. Its checksum is verified before installation, and it is stored separately from NovaMine. Actual device launch and multiplayer compatibility are not yet confirmed.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(12.dp))
        SettingRow("Engine diagnostics", nativeReport)
        SettingRow("World data", "Stored locally in app-private storage. Avoid uninstalling the app until worlds are backed up.")
        SettingRow("Experimental engine", "Falcon is experimental; keep NovaMine installed until device compatibility is confirmed.")
        SettingRow("App version", "1.0.13")
    }
}

@Composable
private fun ConsolePage(logFile: File, onBack: () -> Unit, onStop: () -> Unit) {
    var logText by remember { mutableStateOf("No server log yet. Install the engine, then try starting a server.") }
    fun refreshLogs() {
        logText = if (logFile.isFile) {
            runCatching { logFile.readLines().takeLast(350).joinToString("\n") { formatConsoleLine(it) } }
                .getOrElse { "Could not read log: ${it.message}" }
                .ifBlank { "Log file is empty." }
        } else {
            "No server log yet. Install the engine, then try starting a server."
        }
    }
    androidx.compose.runtime.LaunchedEffect(logFile) { while (true) { refreshLogs(); kotlinx.coroutines.delay(1500) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Server console", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Persisted output from the server process", color = Muted, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { refreshLogs() }) { Text("Refresh") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E3D59))) {
                Text("STOP SERVER")
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxSize().border(1.dp, Line, RoundedCornerShape(12.dp)),
            color = Color(0xFF0B0A10),
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                item {
                    Text(
                        logText,
                        color = Color(0xFFB8F7C8),
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

private fun formatConsoleLine(line: String): String {
    val separator = line.indexOf(' ')
    if (separator != 13) return line
    val millis = line.substring(0, separator).toLongOrNull() ?: return line
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
    return "[$time] ${line.substring(separator + 1)}"
}

@Composable
private fun SettingRow(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(13.dp))
            .background(Panel).border(1.dp, Line, RoundedCornerShape(13.dp)).padding(15.dp)
    ) {
        Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

