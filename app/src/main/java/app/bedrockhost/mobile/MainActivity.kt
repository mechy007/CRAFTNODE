package app.bedrockhost.mobile

import android.content.Intent
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
    Play("Play"), Servers("Servers"), Worlds("Worlds"), Settings("Settings")
}

@Composable
private fun CraftNodeApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.Play) }
    var selectedProfile by remember { mutableStateOf("Survival World") }
    var notice by remember { mutableStateOf("Choose a world to manage your server.") }

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
                    onStart = {
                        val profileId = when (selectedProfile) {
                            "Friends SMP" -> "friends"
                            "Creative Test" -> "creative"
                            else -> "survival"
                        }
                        val intent = Intent(context, ServerHostService::class.java)
                            .setAction(ServerHostService.ACTION_START)
                            .putExtra(ServerHostService.EXTRA_PROFILE_ID, profileId)
                        ContextCompat.startForegroundService(context, intent)
                        notice = "Starting PocketMine-MP. Check Console for real startup logs."
                    },
                    onOpen = { page = it }
                )
                Page.Servers -> ListPage(
                    title = "Your servers",
                    subtitle = "Pick a profile to manage.",
                    entries = listOf("Survival World" to "Default world · Not started", "Friends SMP" to "Private world · Not started", "Creative Test" to "Build world · Not started"),
                    onPick = { selectedProfile = it; notice = "$it selected."; page = Page.Play }
                )
                Page.Worlds -> ListPage(
                    title = "World library",
                    subtitle = "World import and backup controls will live here.",
                    entries = listOf("Survival World" to "Local profile · Backup not created", "Creative Test" to "Local profile · Backup not created"),
                    onPick = { selectedProfile = it; notice = "$it selected."; page = Page.Play }
                )
                Page.Settings -> SettingsPage(onInstallEngine = {
                    val intent = Intent(context, ServerHostService::class.java)
                        .setAction(ServerHostService.ACTION_INSTALL)
                    ContextCompat.startForegroundService(context, intent)
                    notice = "Installing verified PocketMine-MP and Android ARM64 PHP runtime."
                })
            }
            }

            NavigationBar(
                containerColor = Panel,
                contentColor = TextMain,
                windowInsets = WindowInsets.navigationBars
            ) {
                Page.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = {
                            Icon(
                                when (item) {
                                    Page.Play -> Icons.Default.Home
                                    Page.Servers -> Icons.Default.Cloud
                                    Page.Worlds -> Icons.Default.Folder
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
private fun PlayPage(
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    notice: String,
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
                StatusPill("SERVER STATUS", "OFFLINE", Green)
                StatusPill("ACTIVE PLAYERS", "--", Muted)
            }
        }
        item {
            HeroCard(selectedProfile, onSelectProfile, onStart, notice)
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
                ToolCard(Icons.Default.Terminal, "Console", "Logs & commands", Purple, Modifier.weight(1f)) { onOpen(Page.Servers) }
                ToolCard(Icons.Default.Cloud, "Players", "Access & whitelist", TileBlue, Modifier.weight(1f)) { onOpen(Page.Servers) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolCard(Icons.Default.Folder, "Worlds", "Import & backups", TileOrange, Modifier.weight(1f)) { onOpen(Page.Worlds) }
                ToolCard(Icons.Default.Tune, "Settings", "Server properties", TilePink, Modifier.weight(1f)) { onOpen(Page.Settings) }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(16.dp))
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF302448)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Widgets, contentDescription = null, tint = Green, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("CRAFTMIND", color = TextMain, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("Your AI co-admin", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Plan worlds, understand logs and get setup guidance.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        item {
            Text(
                "LOCAL-FIRST  •  ENGINE INTEGRATION IN PROGRESS",
                color = Muted.copy(alpha = 0.72f), fontSize = 9.sp, letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    onStart: () -> Unit,
    notice: String
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
                    Text("  READY TO CONFIGURE", color = Green, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
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
                        Text("Change world", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Gamepad, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("START SERVER", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("POCKETMINE-MP BEDROCK ENGINE", color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp)
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
private fun SettingsPage(onInstallEngine: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Settings", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Customize your CraftNode experience.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        SettingRow("Server software", "PocketMine-MP for Bedrock · Android ARM64 runtime")
        Button(
            onClick = onInstallEngine,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White)
        ) {
            Text("INSTALL BEDROCK ENGINE", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        }
        Text("Downloads official upstream files and verifies SHA-256 before installation. First startup and LAN joining still need testing.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(12.dp))
        SettingRow("Network access", "Remote play needs connectivity testing")
        SettingRow("World storage", "Local-first")
        SettingRow("CraftMind AI", "Not connected")
        SettingRow("App version", "0.1.0 · Preview")
    }
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

