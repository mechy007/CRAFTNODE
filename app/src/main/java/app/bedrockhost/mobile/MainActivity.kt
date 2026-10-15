package app.bedrockhost.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Void = Color(0xFF080B0A)
private val Obsidian = Color(0xFF111714)
private val Slate = Color(0xFF1A231E)
private val Grass = Color(0xFFB6F34A)
private val GrassDark = Color(0xFF426D22)
private val Ice = Color(0xFF8CE8D0)
private val Chalk = Color(0xFFF1F5E9)
private val Dim = Color(0xFF9BA99E)
private val Amber = Color(0xFFFFC857)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Grass,
                secondary = Ice,
                background = Void,
                surface = Obsidian,
                onPrimary = Void,
                onBackground = Chalk,
                onSurface = Chalk
            )) {
                CraftNodeDashboard()
            }
        }
    }
}

@Composable
private fun CraftNodeDashboard() {
    var demoRunning by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Void) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Brush.verticalGradient(listOf(Color(0xFF17251B), Void, Void)))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Grass, RoundedCornerShape(10.dp))
                        .border(2.dp, Color(0xFFD7FF91), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Gamepad, contentDescription = "CraftNode", tint = Void, modifier = Modifier.size(27.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("CRAFTNODE", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Chalk)
                    Text("SERVER CONTROL // 0.1.0", color = Grass, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Dim, modifier = Modifier.size(23.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStat("MODE", "BEDROCK", Modifier.weight(1f))
                MiniStat("PLAYERS", "-- / 10", Modifier.weight(1f))
                MiniStat("UPTIME", "--:--", Modifier.weight(1f))
            }

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF557D34), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Obsidian)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF243A23), Color(0xFF111914))))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).background(if (demoRunning) Grass else Amber, RoundedCornerShape(2.dp))
                        )
                        Text(
                            if (demoRunning) "  PREVIEW STATE: ONLINE" else "  SERVER OFFLINE",
                            color = if (demoRunning) Grass else Amber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp
                        )
                    }
                    Spacer(Modifier.height(15.dp))
                    Text("YOUR WORLD.", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Chalk, lineHeight = 33.sp)
                    Text("YOUR RULES.", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Grass, lineHeight = 33.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Spin up a world for your squad. Manage the server from one place.",
                        color = Color(0xFFC3D0C2), fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { demoRunning = !demoRunning },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Grass,
                            contentColor = Void
                        )
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (demoRunning) "STOP PREVIEW" else "START YOUR SERVER", fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "UI PREVIEW ONLY  •  SERVER ENGINE NOT CONNECTED",
                        color = Dim, fontSize = 9.sp, letterSpacing = 0.8.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LOADOUT", color = Grass, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("SERVER TOOLS", color = Dim, fontSize = 10.sp, letterSpacing = 1.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile(Icons.Default.Terminal, "LIVE CONSOLE", "Logs & commands", Modifier.weight(1f))
                ToolTile(Icons.Default.People, "PLAYERS", "Whitelist & access", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile(Icons.Default.Cloud, "WORLD SAVE", "Import & backups", Modifier.weight(1f))
                ToolTile(Icons.Default.Memory, "PERFORMANCE", "RAM & device load", Modifier.weight(1f))
            }

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF394638), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Slate),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).background(Color(0xFF2B4529), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Grass, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("CRAFTMIND", color = Chalk, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("Your AI co-admin", color = Grass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Diagnose logs, tune settings and build world ideas.", color = Dim, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            Text(
                "BUILT FOR YOUR CREW  •  LOCAL-FIRST",
                color = Color(0xFF637368), fontSize = 9.sp, letterSpacing = 1.4.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Obsidian, RoundedCornerShape(9.dp))
            .border(1.dp, Color(0xFF2A3A2D), RoundedCornerShape(9.dp))
            .padding(vertical = 12.dp, horizontal = 9.dp)
    ) {
        Text(label, color = Dim, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = Chalk, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ToolTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(112.dp).border(1.dp, Color(0xFF2E4031), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Obsidian),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = Grass, modifier = Modifier.size(23.dp))
            Column {
                Text(title, color = Chalk, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Dim, fontSize = 10.sp)
            }
        }
    }
}
