package app.bedrockhost.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Router
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

private val Midnight = Color(0xFF090A14)
private val Panel = Color(0xFF141629)
private val Violet = Color(0xFF9A6BFF)
private val Cyan = Color(0xFF50E5FF)
private val Muted = Color(0xFFAAAEC8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Violet,
                secondary = Cyan,
                background = Midnight,
                surface = Panel
            )) {
                CraftNodeDashboard()
            }
        }
    }
}

@Composable
private fun CraftNodeDashboard() {
    var serverRunning by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF15102B), Midnight, Midnight))
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(listOf(Violet, Cyan)),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = "CraftNode portal", tint = Midnight)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("CRAFTNODE", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Your world. Your server. Your rules.", color = Muted, fontSize = 12.sp)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("YOUR SERVER", color = Muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(if (serverRunning) Cyan else Color(0xFFFFC46B), RoundedCornerShape(50))
                        )
                        Text(
                            if (serverRunning) " Demo state: running" else " Not running",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (serverRunning) "UI demonstration only — no server engine is connected yet."
                        else "Your server controls will appear here once the Android engine is validated.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { serverRunning = !serverRunning },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (serverRunning) "Demo: Stop" else "Demo: Start", color = Color.White)
                    }
                    Text(
                        "Preview UI only; this button does not start a Minecraft server.",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Text("CONTROL CENTER", color = Muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
            FeatureCard(Icons.Default.Router, "Remote multiplayer", "Remote connection diagnostics and join details — not active yet.")
            FeatureCard(Icons.Default.Terminal, "Live console", "Engine logs and supported commands will connect after runtime validation.")
            FeatureCard(Icons.Default.Memory, "Performance", "Device and server metrics will appear when real measurements are available.")

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1530))) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Cyan)
                        Text("  CRAFTMIND AI", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Six planned capabilities", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Server Admin · Error Fixer · Performance Coach\nWorld Creator · Add-on Assistant · Multiplayer Helper",
                        color = Color(0xFFE0D9FF),
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("AI is not connected in this preview.", color = Muted, fontSize = 11.sp)
                }
            }

            Text("CRAFTNODE 0.1.0 • FOUNDATION BUILD", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}
