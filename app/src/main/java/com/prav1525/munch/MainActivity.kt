package com.prav1525.munch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Void = Color(0xFF080B12)
private val SurfaceDark = Color(0xFF111722)
private val SurfaceLift = Color(0xFF171F2D)
private val Violet = Color(0xFFB9A3FF)
private val Cyan = Color(0xFF8DE8E0)
private val White = Color(0xFFF3F2FA)
private val Soft = Color(0xFF9BA6B9)
private val Hairline = Color(0xFF283143)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MunchHome() } }
    }
}

@Composable
private fun MunchHome() {
    var selectedAction by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Void) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF111322), Void, Void)))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Violet, Cyan))),
                        contentAlignment = Alignment.Center
                    ) { Text("m", color = Void, fontSize = 28.sp, fontWeight = FontWeight.Black) }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("munch", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                        Text("YOUR PERSONAL AI", color = Soft, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                }
                Box(
                    modifier = Modifier.clip(CircleShape).border(1.dp, Hairline, CircleShape)
                        .background(SurfaceDark).clickable { showAbout = !showAbout }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(if (showAbout) "v0.2 • PREVIEW" else "ABOUT  ⤢", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (showAbout) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Hairline)) {
                    Text(
                        "Munch is growing into a personal assistant for everyday life, with voice, music, navigation and more added in stages. This screen is a design preview; assistant actions are not connected yet.",
                        color = Soft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("A LITTLE MORE", color = Soft, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.SemiBold)
                Text("Your world.\nOne conversation away.", color = White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.9).sp)
                Text("Think out loud. Make a plan. Find your next idea.", color = Soft, fontSize = 14.sp, lineHeight = 21.sp)
            }

            AssistantOrb()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WHAT'S ON YOUR MIND?", color = Soft, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { selectedAction = "conversation" },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Void)
                ) { Text("✳   Start a conversation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (selectedAction == "conversation") {
                    Text("Voice chat is the next build milestone. For now, this is a preview of the conversation entry point.", color = Cyan, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("YOUR SHORTCUTS", color = Soft, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                    Text("PERSONAL SPACE", color = Violet, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard("✦", "Ask anything", "Ideas & answers", Modifier.weight(1f)) { selectedAction = "ask" }
                    ShortcutCard("♫", "Your music", "Play what fits", Modifier.weight(1f)) { selectedAction = "music" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard("⌁", "Get somewhere", "Routes & places", Modifier.weight(1f)) { selectedAction = "places" }
                    ShortcutCard("◷", "My day", "Plans & reminders", Modifier.weight(1f)) { selectedAction = "day" }
                }
                selectedAction?.takeIf { it != "conversation" }?.let { action ->
                    val detail = when (action) {
                        "ask" -> "Everyday questions and personal answers are planned for the conversation module."
                        "music" -> "Music-app controls will be added as a separate capability."
                        "places" -> "Place search and navigation will be added as an optional capability."
                        else -> "Planning and reminders are part of the future personal-assistant roadmap."
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceLift), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Hairline)) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("COMING IN FUTURE BUILDS", color = Violet, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Text(detail, color = Soft, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(Cyan, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text("MADE TO ADAPT TO YOU", color = Soft, fontSize = 9.sp, letterSpacing = 1.8.sp)
            }
        }
    }
}

@Composable
private fun AssistantOrb() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF34315A), Color(0xFF1B253C), Void)))
                .border(1.dp, Color(0x553D4964), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(0.68f).aspectRatio(1f)
                    .border(1.dp, Color(0x668DE8E0), CircleShape).padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF9D91FF), Color(0xFF46547C), Color(0xFF172232)))),
                    contentAlignment = Alignment.Center
                ) { Text("m", color = White, fontSize = 74.sp, fontWeight = FontWeight.Light, letterSpacing = (-5).sp) }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("HERE WHEN YOU NEED ME", color = White, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
            Text("Voice-first • Built around you", color = Soft, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ShortcutCard(icon: String, title: String, detail: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, color = Violet, fontSize = 22.sp)
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Soft, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
