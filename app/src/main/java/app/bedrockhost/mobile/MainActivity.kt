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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
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

// NetherCore-inspired obsidian palette with Minecraft inventory-slot edges.
private val Background = Color(0xFF07090E)
private val Panel = Color(0xFF111622)
private val Line = Color(0xFF26334A)
private val TextMain = Color(0xFFE8EEF8)
private val Muted = Color(0xFF91A0B7)
private val Purple = Color(0xFF10B981)
private val Green = Color(0xFF34D399)
private val TileBlue = Color(0xFF06B6D4)
private val TileOrange = Color(0xFFF59E0B)
private val TilePink = Color(0xFFA855F7)
private val ObsidianRaised = Color(0xFF161C2B)
private val Redstone = Color(0xFFEF4444)

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
    Play("Home"), Host("Host"), Remote("Remote"), Console("Console"), Worlds("Worlds"), Players("Players"), Settings("Settings")
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
                Brush.verticalGradient(listOf(Color(0xFF0C201B), Background, Background))
            )
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF0C0F17))
                    .border(width = 1.dp, color = Line)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Green, Color(0xFF047857))))
                        .border(2.dp, Color(0xFF6EE7B7), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.craftnode_mark),
                        contentDescription = "CraftNode logo",
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(7.dp))
                    )
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CRAFT", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                        Text("NODE", color = Green, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    }
                    Text("BEDROCK CONTROL CENTER", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.25.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (serverRunning) Green else Redstone))
                        Spacer(Modifier.width(5.dp))
                        Text(if (serverRunning) "ONLINE PROCESS" else "OFFLINE", color = if (serverRunning) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("v1.0.26", color = Muted, fontSize = 9.sp)
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
                            page = Page.Host
                        }
                    },
                    onOpen = { page = it }
                )
                Page.Host -> LocalHostDashboard(onOpenConsole = { page = Page.Console })
                Page.Worlds -> WorldManagerPage(
                    selectedProfile = selectedProfile,
                    onSelectProfile = { selectedProfile = it; notice = "$it selected." },
                    onBack = { page = Page.Play }
                )
                Page.Players -> PlayerManagerPage(onBack = { page = Page.Play })
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
                containerColor = Color(0xFF0C0F17),
                contentColor = TextMain,
                windowInsets = WindowInsets.navigationBars
            ) {
                listOf(Page.Play, Page.Worlds, Page.Console, Page.Settings).forEach { item ->
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
                                    Page.Worlds -> Icons.Default.Folder
                                    Page.Players -> Icons.Default.Groups
                                    Page.Settings -> Icons.Default.Settings
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF04120D),
                            selectedTextColor = Green,
                            indicatorColor = Green,
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
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
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
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF49366D), RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("PLAYIT · NATIVE BETA", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(8.dp))
                Text("Free public tunnel option", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "CraftNode can create a separate self-managed Playit agent using its bundled official Android ARM64 CLI and daemon. Tap Create CraftNode agent, approve it in the browser, then refresh status. This is experimental: verify the agent and tunnel on-device and test a real join from another network. Keep the Bedrock server on UDP 19132.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                var nativeStatus by remember { mutableStateOf("Create a CraftNode agent to set up Playit automatically.") }
                var nativeClaimUrl by remember { mutableStateOf("") }
                var nativeAuthorized by remember { mutableStateOf(false) }
                var nativeEndpoints by remember { mutableStateOf("") }
                var nativeTunnelState by remember { mutableStateOf("unknown") }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    while (true) {
                        val playitPrefs = context.getSharedPreferences(app.bedrockhost.mobile.multiplayer.NativePlayitService.PREFS, 0)
                        nativeStatus = playitPrefs.getString(app.bedrockhost.mobile.multiplayer.NativePlayitService.KEY_STATUS, nativeStatus) ?: nativeStatus
                        nativeClaimUrl = playitPrefs.getString(app.bedrockhost.mobile.multiplayer.NativePlayitService.KEY_CLAIM_URL, nativeClaimUrl) ?: nativeClaimUrl
                        nativeAuthorized = playitPrefs.getBoolean(app.bedrockhost.mobile.multiplayer.NativePlayitService.KEY_AUTHORIZED, false)
                        nativeEndpoints = playitPrefs.getString(app.bedrockhost.mobile.multiplayer.NativePlayitService.KEY_ENDPOINTS, "") ?: ""
                        nativeTunnelState = playitPrefs.getString(app.bedrockhost.mobile.multiplayer.NativePlayitService.KEY_TUNNEL_STATE, "unknown") ?: "unknown"
                        kotlinx.coroutines.delay(1500)
                    }
                }
                Text("NATIVE PLAYIT AGENT", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Create a Playit agent here, approve it in the browser, and CraftNode will receive and install the generated agent key automatically. You do not need to copy or paste a secret key. The key is handled by the app's private agent flow.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ContextCompat.startForegroundService(context, Intent(context, app.bedrockhost.mobile.multiplayer.NativePlayitService::class.java).setAction(app.bedrockhost.mobile.multiplayer.NativePlayitService.ACTION_STATUS))
                        nativeStatus = "Refreshing Playit tunnel state and endpoint…"
                    }, modifier = Modifier.weight(1f)) { Text("Refresh tunnel") }
                    OutlinedButton(onClick = {
                        ContextCompat.startForegroundService(context, Intent(context, app.bedrockhost.mobile.multiplayer.NativePlayitService::class.java).setAction(app.bedrockhost.mobile.multiplayer.NativePlayitService.ACTION_STOP))
                        nativeStatus = "Stop requested."
                    }, modifier = Modifier.weight(1f)) { Text("Stop agent") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        ContextCompat.startForegroundService(context, Intent(context, app.bedrockhost.mobile.multiplayer.NativePlayitService::class.java).setAction(app.bedrockhost.mobile.multiplayer.NativePlayitService.ACTION_FORGET_SECRET))
                        nativeStatus = "Agent stopped and local authorization cleared."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("FORGET PLAYIT AGENT ON THIS DEVICE") }
                Text(if (nativeAuthorized) "✓ VERIFIED · Agent authorization accepted by Playit" else nativeStatus,
                    color = if (nativeAuthorized) Green else Green, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF100D17)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(12.dp))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("MINECRAFT SERVER ADDRESS", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (nativeEndpoints.isBlank()) "No Playit endpoint received yet. Approve the agent, wait for a tunnel, then tap Refresh tunnel."
                            else nativeEndpoints,
                            color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Tunnel state: $nativeTunnelState · Share only the address and port, never the agent key.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (nativeEndpoints.isNotBlank()) {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(nativeEndpoints))
                                    nativeStatus = "Playit address copied. Add it in Minecraft → Servers → Add Server."
                                } else nativeStatus = "No public Playit address is available to copy yet."
                            },
                            enabled = nativeEndpoints.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("COPY SERVER ADDRESS + PORT") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!nativeAuthorized) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            nativeClaimUrl = ""
                            context.getSharedPreferences(app.bedrockhost.mobile.multiplayer.NativePlayitService.PREFS, 0)
                                .edit().remove(app.bedrockhost.mobile.multiplayer.NativePlayitService.KEY_CLAIM_URL).apply()
                            val claimIntent = Intent(context, app.bedrockhost.mobile.multiplayer.NativePlayitService::class.java)
                                .setAction(app.bedrockhost.mobile.multiplayer.NativePlayitService.ACTION_CLAIM)
                            ContextCompat.startForegroundService(context, claimIntent)
                            nativeStatus = "Creating a separate CraftNode agent claim..."
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Create CraftNode agent") }
                    OutlinedButton(
                        onClick = {
                            if (nativeClaimUrl.startsWith("https://playit.gg/claim/")) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(nativeClaimUrl))) }
                                    .onFailure { nativeStatus = "Could not open Playit claim page: ${it.message ?: "unknown error"}" }
                            } else nativeStatus = "Create the CraftNode agent first."
                        },
                        modifier = Modifier.weight(1f),
                        enabled = nativeClaimUrl.startsWith("https://playit.gg/claim/")
                    ) { Text("Approve in browser") }
                }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val setup = "pkg update -y\npkg install tur-repo -y\npkg install playit -y\n# Start the daemon (keep this Termux session running):\nplayitd &\n# Then open the CLI setup in another Termux command:\nplayit-cli"
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(setup))
                        notice = "Copied Termux companion commands. Review docs/PLAYIT_ANDROID_COMPANION.md before running; this does not start a tunnel from CraftNode."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("COPY TERMUX SETUP COMMANDS") }
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
                val stopped = context.stopService(serviceIntent)
                notice = if (stopped) "Tunnel stop requested." else "No active tunnel service was running."
                authToken = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("STOP REMOTE TUNNEL") }
        Spacer(Modifier.height(8.dp))
        val parsedPublicPort = publicPort.toIntOrNull()
        OutlinedButton(
            onClick = {
                val endpoint = "${relayHost.trim()}:${parsedPublicPort}"
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(endpoint))
                notice = "Copied configured endpoint. The relay and Bedrock join are not verified yet."
            },
            enabled = relayHost.isNotBlank() && parsedPublicPort != null && parsedPublicPort in 1..65535,
            modifier = Modifier.fillMaxWidth()
        ) { Text("COPY JOIN ADDRESS") }
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CRAFTNODE / CONTROL CENTER", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text("Server overview", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                    Text("Everything for your Bedrock world, in one place.", color = Muted, fontSize = 12.sp)
                }
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF164E3D), Color(0xFF0D2420))))
                        .border(1.dp, Color(0xFF27765B), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(id = R.drawable.craftnode_mark), contentDescription = "CraftNode", modifier = Modifier.size(35.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewMetric(
                    label = "SERVER PROCESS",
                    value = if (serverRunning) "RUNNING" else "STOPPED",
                    detail = if (serverRunning) "Process launch detected" else "Not currently running",
                    accent = if (serverRunning) Green else Redstone,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    label = "ONLINE PLAYERS",
                    value = "—",
                    detail = "Not reported by engine",
                    accent = TileBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            HeroCard(selectedProfile, onSelectProfile, onStart, notice, serverRunning)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MANAGE YOUR SERVER", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
                    Text("Jump straight to a control panel", color = Muted, fontSize = 11.sp)
                }
                Box(Modifier.clip(RoundedCornerShape(7.dp)).background(ObsidianRaised).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text("TOOLS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
        }
        item {
            DashboardTool(
                icon = Icons.Default.Terminal,
                title = "Live console",
                subtitle = "Inspect logs and server messages",
                accent = TileBlue,
                onClick = { onOpen(Page.Console) }
            )
        }
        item {
            DashboardTool(
                icon = Icons.Default.Folder,
                title = "World manager",
                subtitle = "Choose a profile and manage world files",
                accent = TileOrange,
                onClick = { onOpen(Page.Worlds) }
            )
        }
        item {
            DashboardTool(
                icon = Icons.Default.Cloud,
                title = "Remote access",
                subtitle = "Playit tunnel and connection diagnostics",
                accent = TileBlue,
                onClick = { onOpen(Page.Remote) }
            )
        }
        item {
            DashboardTool(
                icon = Icons.Default.Settings,
                title = "Engine & versions",
                subtitle = "Runtime installation and server engine options",
                accent = Muted,
                onClick = { onOpen(Page.Settings) }
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF171A21)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TileBlue, modifier = Modifier.size(17.dp))
                Column {
                    Text("HONEST STATUS", color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "A running process does not guarantee that Minecraft clients can connect. Player counts and network reachability stay unconfirmed until the engine or tunnel reports verified data.",
                        color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, Line, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun DashboardTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Panel).border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.13f)).border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Open $title", tint = Muted, modifier = Modifier.size(19.dp))
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
    var showWorldPicker by remember { mutableStateOf(false) }
    if (showWorldPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWorldPicker = false },
            title = { Text("Switch world") },
            text = {
                Column {
                    listOf("Friends SMP", "Creative Test", "Survival World").forEach { world ->
                        androidx.compose.material3.TextButton(
                            onClick = { onSelectProfile(world); showWorldPicker = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedProfile == world) "✓  $world" else world, color = if (selectedProfile == world) Green else TextMain)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, if (serverRunning) Color(0xFF16845D) else Line, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Box {
            Canvas(Modifier.fillMaxWidth().height(265.dp)) {
                val tile = 24.dp.toPx()
                val colors = listOf(Color(0xFF172D2A), Color(0xFF111E22))
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
                    brush = Brush.horizontalGradient(listOf(Background.copy(alpha = 0.97f), Background.copy(alpha = 0.80f), Background.copy(alpha = 0.18f))),
                    size = size
                )
                drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Panel.copy(alpha = 0.98f))), size = size)
            }
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (serverRunning) Green else Redstone))
                    Spacer(Modifier.width(7.dp))
                    Text(if (serverRunning) "SERVER PROCESS ACTIVE" else "LOCAL WORLD • READY TO HOST", color = if (serverRunning) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    Spacer(Modifier.weight(1f))
                    Text("●", color = if (serverRunning) Green else Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(23.dp))
                Text(selectedProfile.uppercase(), color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp, letterSpacing = 0.4.sp)
                Text("Your world. Your server. Your rules.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showWorldPicker = true },
                        shape = RoundedCornerShape(9.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Switch world", fontSize = 11.sp)
                    }
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1.25f).height(44.dp),
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (serverRunning) Redstone else Green, contentColor = Color(0xFF06110D))
                    ) {
                        Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (serverRunning) "STOP SERVER" else "CONFIGURE & HOST", fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.1.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(Green))
                    Text(notice, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Panel)
            .border(1.dp, Line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.size(6.dp).clip(CircleShape).background(valueColor))
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
        modifier = modifier.height(105.dp).clickable(onClick = onClick)
            .border(1.dp, Line, RoundedCornerShape(13.dp)),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.13f))
                    .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("↗", color = Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(title, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Muted, fontSize = 9.sp, maxLines = 1)
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
        SettingRow("App version", "1.0.26")
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

