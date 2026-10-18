package com.prav1525.munch

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101820)
private val Panel = Color(0xFF1B2833)
private val Mint = Color(0xFF8EF0C0)
private val Muted = Color(0xFFB4C1CB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MunchHome()
            }
        }
    }
}

@Composable
private fun MunchHome() {
    var previewExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MUNCH",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "YOUR RIDE. YOUR ASSISTANT.",
                        color = Muted,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Panel, CircleShape)
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Text("PHASE 01", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("BIKE ASSISTANT", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF263B43), CircleShape)
                            .padding(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("M", color = Mint, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Ride focused.\nStay connected.",
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The first step toward a voice-first assistant built for the road — and ready to grow beyond it.",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { previewExpanded = !previewExpanded },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)
                    ) {
                        Text(
                            text = if (previewExpanded) "HIDE BUILD STATUS" else "EXPLORE FIRST BUILD",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (previewExpanded) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Prototype UI only — microphone, wake word, music controls, and navigation are not connected yet.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Text("BUILT FOR THE RIDE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)

            FeatureRow(title = "Voice first", detail = "Wake phrase and voice commands", tag = "PLANNED")
            FeatureRow(title = "Music + maps", detail = "Control playback and start a route", tag = "PLANNED")
            FeatureRow(title = "Bluetooth audio", detail = "Designed for your OnePlus neckband", tag = "PLANNED")

            Spacer(Modifier.weight(1f))
            Text(
                text = "START WITH THE BIKE. GROW INTO YOUR EVERYDAY ASSISTANT.",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FeatureRow(title: String, detail: String, tag: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(detail, color = Muted, fontSize = 12.sp)
            }
            Text(tag, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}
