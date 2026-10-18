package com.prav1525.munch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.core.content.ContextCompat
import java.util.Locale

private val Void = Color(0xFF080B12)
private val SurfaceDark = Color(0xFF111722)
private val SurfaceLift = Color(0xFF171F2D)
private val Violet = Color(0xFFB9A3FF)
private val Cyan = Color(0xFF8DE8E0)
private val White = Color(0xFFF3F2FA)
private val Soft = Color(0xFF9BA6B9)
private val Hairline = Color(0xFF283143)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var selectedVoiceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedVoiceName = getSharedPreferences("munch_preferences", MODE_PRIVATE).getString("tts_voice_name", null)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val voices = engine.voices.orEmpty()
                val saved = selectedVoiceName?.let { name -> voices.firstOrNull { it.name == name } }
                val british = voices.filter { it.locale.language == "en" && it.locale.country == "GB" }
                val preferred = british.firstOrNull { it.name.contains("female", ignoreCase = true) || it.name.contains("woman", ignoreCase = true) }
                    ?: british.firstOrNull()
                    ?: voices.firstOrNull { it.locale.language == "en" }
                val chosen = saved ?: preferred
                if (chosen != null) {
                    engine.voice = chosen
                    selectedVoiceName = chosen.name
                } else engine.language = Locale.UK
            }
        }
        setContent { MaterialTheme { MunchHome(this) } }
    }

    fun availableVoices(): List<Voice> = tts?.voices.orEmpty()
        .filter { it.locale.language == "en" }
        .sortedWith(compareBy<Voice>({ it.locale.country != "GB" }, { it.name }))

    fun currentVoiceName(): String = tts?.voice?.let { it.locale.displayName + " — " + it.name } ?: "System default"

    fun isVoiceSelected(name: String): Boolean = tts?.voice?.name == name || selectedVoiceName == name

    fun selectVoice(name: String) {
        val voice = tts?.voices?.firstOrNull { it.name == name } ?: return
        runCatching { tts?.voice = voice }.onSuccess {
            selectedVoiceName = voice.name
            getSharedPreferences("munch_preferences", MODE_PRIVATE).edit().putString("tts_voice_name", voice.name).apply()
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "munch-response")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

private data class ChatLine(val speaker: String, val message: String)

@Composable
private fun MunchHome(activity: MainActivity) {
    val context = activity
    var selectedAction by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var selectedVoiceLabel by remember { mutableStateOf(activity.currentVoiceName()) }
    var showConversation by remember { mutableStateOf(false) }
    var showMapDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }
    var reminderText by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf("10") }
    var prompt by remember { mutableStateOf("") }
    var groqApiKey by remember { mutableStateOf(AssistantServices.getGroqApiKey(context)) }
    var onlineFallbackEnabled by remember { mutableStateOf(AssistantServices.isGroqFallbackEnabled(context)) }
    var wakeEnabled by remember { mutableStateOf(context.getSharedPreferences("munch_preferences", android.content.Context.MODE_PRIVATE).getBoolean("wake_word_enabled", false)) }
    var pendingWakeEnable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready when you are") }
    val messages = remember { mutableStateListOf<ChatLine>().apply {
        AssistantServices.readHistory(context).forEach { add(ChatLine(it.first, it.second)) }
    } }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                prompt = spoken
                runAssistantRequest(context, activity, spoken, messages, { busy = it }, { statusText = it }, { prompt = "" })
            } else statusText = "I didn't catch that. Try again."
        } else statusText = "Voice input was cancelled or unavailable."
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingWakeEnable) {
            pendingWakeEnable = false
            context.getSharedPreferences("munch_preferences", android.content.Context.MODE_PRIVATE).edit().putBoolean("wake_word_enabled", true).apply()
            wakeEnabled = true
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
            statusText = "Hey Munch listening is on. Keep the persistent notification visible."
        } else if (granted) launchSpeech(speechLauncher) { statusText = it }
        else {
            pendingWakeEnable = false
            wakeEnabled = false
            statusText = "Microphone permission is needed for voice conversations and wake-word listening."
        }
    }

    fun beginVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchSpeech(speechLauncher) { statusText = it }
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val wakeCommand = activity.intent.getStringExtra("munch_wake_command")
    val wakeListenOnly = activity.intent.getBooleanExtra("munch_wake_listen", false)
    LaunchedEffect(wakeCommand, wakeListenOnly) {
        if (!wakeCommand.isNullOrBlank()) {
            showConversation = true
            runAssistantRequest(context, activity, wakeCommand, messages, { busy = it }, { statusText = it }, { prompt = "" })
            activity.intent.removeExtra("munch_wake_command")
        } else if (wakeListenOnly) {
            showConversation = true
            beginVoice()
            activity.intent.removeExtra("munch_wake_listen")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Void) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Violet, Cyan))), contentAlignment = Alignment.Center) {
                        Text("m", color = Void, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("munch", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                        Text("YOUR PERSONAL AI", color = Soft, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).border(1.dp, Hairline, CircleShape).background(SurfaceDark)
                        .clickable { showSettings = true }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("SETTINGS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.clip(CircleShape).border(1.dp, Hairline, CircleShape).background(SurfaceDark)
                        .clickable { showAbout = !showAbout }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(if (showAbout) "CLOSE" else "ABOUT", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (showAbout) {
                InfoCard("ABOUT MUNCH", "Your assistant for voice, app launching, music search, navigation and reminders. Groq can answer open-ended questions when configured; common commands have an offline fallback. “Hey Munch” listening is optional and depends on Android speech-recognition and background-microphone support.")
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("A LITTLE MORE", color = Soft, fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.SemiBold)
                Text("Your world.\nOne conversation away.", color = White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.9).sp)
                Text("Think out loud. Make a plan. Find your next idea.", color = Soft, fontSize = 14.sp, lineHeight = 21.sp)
            }

            AssistantOrb()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WHAT'S ON YOUR MIND?", color = Soft, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                Button(onClick = { showConversation = true; beginVoice() }, modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Void)) {
                    Text(if (busy) "◌  Thinking…" else "✳   Start a conversation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(statusText, color = Cyan, fontSize = 12.sp, lineHeight = 18.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("YOUR SHORTCUTS", color = Soft, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                    Text("PERSONAL SPACE", color = Violet, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard("✦", "Ask anything", "Ideas & answers", Modifier.weight(1f)) { showConversation = true }
                    ShortcutCard("♫", "Your music", "Open music app", Modifier.weight(1f)) { AssistantServices.openMusic(context); statusText = "Opening your music app" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard("⌁", "Get somewhere", "Routes & places", Modifier.weight(1f)) { showMapDialog = true }
                    ShortcutCard("◷", "My day", "Plans & reminders", Modifier.weight(1f)) { showReminderDialog = true }
                }
            }

            if (showConversation) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Hairline)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("YOUR CONVERSATION", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            TextButton(onClick = { showConversation = false }) { Text("CLOSE", color = Cyan) }
                        }
                        if (messages.isEmpty()) Text("Ask a question by voice or type below. Your conversation is saved on this device.", color = Soft, fontSize = 13.sp)
                        messages.takeLast(12).forEach { line ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (line.speaker == "user") "YOU" else "MUNCH", color = if (line.speaker == "user") Violet else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(line.message, color = White, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ask Munch") }, shape = RoundedCornerShape(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { beginVoice() }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceLift)) { Text("🎙 Voice", color = Cyan) }
                            Button(onClick = {
                                val q = prompt.trim()
                                if (q.isNotEmpty() && !busy) runAssistantRequest(context, activity, q, messages, { busy = it }, { statusText = it }, { prompt = "" })
                            }, enabled = !busy && prompt.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Void)) {
                                Text(if (busy) "Thinking…" else "Send")
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(Cyan, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text("MADE TO ADAPT TO YOU", color = Soft, fontSize = 9.sp, letterSpacing = 1.8.sp)
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = SurfaceDark,
            title = { Text("Munch settings", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Assistant voice", color = Cyan, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { selectedVoiceLabel = activity.currentVoiceName(); showVoicePicker = true }) {
                        Text(selectedVoiceLabel, color = White)
                    }
                    Text("Choose an installed English voice. British voices are listed first; voice gender labels depend on your phone's speech engine.", color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    Text("Local-first hybrid AI", color = Cyan, fontWeight = FontWeight.SemiBold)
                    Text("Native actions such as opening apps, music search, Maps and reminders run locally first. For open-ended questions, Groq is the primary AI when a key is saved and online AI is enabled; if it fails, Munch gives a concise offline fallback. Your API key is stored in app-private preferences, not bundled into source code.", color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    OutlinedTextField(
                        value = groqApiKey,
                        onValueChange = { groqApiKey = it; AssistantServices.saveGroqApiKey(context, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Groq API key") },
                        placeholder = { Text("Paste your Groq key") },
                        singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Allow online fallback", color = White, fontSize = 13.sp)
                            Text(if (onlineFallbackEnabled && groqApiKey.isNotBlank()) "Groq primary • offline fallback" else if (onlineFallbackEnabled) "Add a key to enable Groq" else "Offline only", color = Soft, fontSize = 11.sp)
                        }
                        Switch(checked = onlineFallbackEnabled, onCheckedChange = {
                            onlineFallbackEnabled = it
                            AssistantServices.setGroqFallbackEnabled(context, it)
                        })
                    }
                    Text("Hands-free wake phrase", color = Cyan, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Listen for “Hey Munch”", color = White, fontSize = 13.sp)
                            Text(if (wakeEnabled) "Listening service enabled" else "Off • uses microphone while enabled", color = Soft, fontSize = 11.sp)
                        }
                        Switch(checked = wakeEnabled, onCheckedChange = { enabled ->
                            if (enabled) {
                                pendingWakeEnable = true
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    pendingWakeEnable = false
                                    context.getSharedPreferences("munch_preferences", android.content.Context.MODE_PRIVATE).edit().putBoolean("wake_word_enabled", true).apply()
                                    wakeEnabled = true
                                    ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
                                    statusText = "Hey Munch listening is on."
                                } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                pendingWakeEnable = false
                                wakeEnabled = false
                                context.getSharedPreferences("munch_preferences", android.content.Context.MODE_PRIVATE).edit().putBoolean("wake_word_enabled", false).apply()
                                context.stopService(Intent(context, WakeWordService::class.java))
                                statusText = "Wake-word listening stopped."
                            }
                        })
                    }
                    Text("Wake-word detection uses Android speech recognition and may require network access; it is not a guaranteed offline keyword engine. Android may stop microphone access under battery or background restrictions.", color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    Text("Free Groq limits and availability can change. If a request fails or times out, Munch returns its offline response instead of waiting indefinitely.", color = Soft, fontSize = 11.sp, lineHeight = 16.sp)
                    Text("Saved conversations: ${messages.size}", color = Soft, fontSize = 12.sp)
                    TextButton(onClick = { AssistantServices.clearHistory(context); messages.clear(); statusText = "Conversation history cleared" }) {
                        Text("Clear conversation history", color = Violet)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false; statusText = "Offline mode ready" }) { Text("DONE", color = Cyan) } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("CANCEL", color = Soft) } }
        )
    }

    if (showVoicePicker) {
        val voices = activity.availableVoices()
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            containerColor = SurfaceDark,
            title = { Text("Choose assistant voice", color = White) },
            text = {
                Column(Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (voices.isEmpty()) {
                        Text("No English voices are available yet. Install an English voice in your Android Text-to-speech settings and try again.", color = Soft, fontSize = 12.sp)
                    }
                    voices.forEach { voice ->
                        val label = voice.locale.displayName + " — " + voice.name
                        Row(Modifier.fillMaxWidth().clickable {
                            activity.selectVoice(voice.name)
                            selectedVoiceLabel = activity.currentVoiceName()
                            showVoicePicker = false
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = activity.isVoiceSelected(voice.name), onClick = {
                                activity.selectVoice(voice.name)
                                selectedVoiceLabel = activity.currentVoiceName()
                                showVoicePicker = false
                            })
                            Column(Modifier.weight(1f)) {
                                Text(label, color = White, fontSize = 12.sp, lineHeight = 16.sp)
                                Text((if (voice.locale.country == "GB") "British English" else voice.locale.displayLanguage) + " · " + (if (voice.isNetworkConnectionRequired) "Network voice" else "On-device voice"), color = Soft, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("DONE", color = Cyan) } }
        )
    }

    if (showMapDialog) {
        AlertDialog(onDismissRequest = { showMapDialog = false }, containerColor = SurfaceDark,
            title = { Text("Where to?", color = White) },
            text = { OutlinedTextField(value = destination, onValueChange = { destination = it }, label = { Text("Destination") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (destination.isNotBlank()) { AssistantServices.openMaps(context, destination.trim()); statusText = "Opening navigation"; showMapDialog = false } }) { Text("NAVIGATE", color = Cyan) } },
            dismissButton = { TextButton(onClick = { showMapDialog = false }) { Text("CANCEL", color = Soft) } })
    }

    if (showReminderDialog) {
        AlertDialog(onDismissRequest = { showReminderDialog = false }, containerColor = SurfaceDark,
            title = { Text("Create a reminder", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = reminderText, onValueChange = { reminderText = it }, label = { Text("What should I remind you?") })
                    OutlinedTextField(value = reminderMinutes, onValueChange = { reminderMinutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("In how many minutes?") }, singleLine = true)
                    Text("Exact reminders may require alarm permission in Android settings. Notifications must also be allowed.", color = Soft, fontSize = 11.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                val mins = reminderMinutes.toLongOrNull()
                if (reminderText.isNotBlank() && mins != null && mins > 0) {
                    val scheduled = runCatching { AssistantServices.scheduleReminder(context, reminderText.trim(), System.currentTimeMillis() + mins * 60_000L) }.getOrDefault(false)
                    statusText = if (scheduled) "Reminder scheduled for ${mins} minutes from now" else "Allow exact alarms in Android settings, then try again"
                    showReminderDialog = false
                }
            }) { Text("SET REMINDER", color = Cyan) } },
            dismissButton = { TextButton(onClick = { showReminderDialog = false }) { Text("CANCEL", color = Soft) } })
    }
}

private fun launchSpeech(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    status: (String) -> Unit
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Munch")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        launcher.launch(intent)
        status("Listening through Android speech recognition…")
    } catch (_: Exception) {
        status("Speech recognition is unavailable on this device.")
    }
}

private fun runAssistantRequest(
    context: android.content.Context,
    activity: MainActivity,
    prompt: String,
    messages: MutableList<ChatLine>,
    setBusy: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    clearPrompt: () -> Unit
) {
    messages.add(ChatLine("user", prompt))
    AssistantServices.saveMessage(context, "user", prompt)
    clearPrompt()
    val lower = prompt.lowercase(Locale.ROOT)
    when {
        lower.startsWith("navigate to ") || lower.startsWith("take me to ") || lower.startsWith("directions to ") -> {
            val destination = prompt.substringAfter(" to ", "").trim()
            if (destination.isNotBlank()) {
                AssistantServices.openMaps(context, destination)
                val answer = "Opening Google Maps for $destination."
                messages.add(ChatLine("assistant", answer)); AssistantServices.saveMessage(context, "assistant", answer)
                setStatus(answer); activity.speak(answer)
            } else setStatus("Tell me the destination you want.")
        }
        Regex("^play\\s+(.+)$", RegexOption.IGNORE_CASE).matches(prompt.trim()) &&
            !lower.matches(Regex("^play\\s+(music|my music|some music|the music)[.! ]*$")) -> {
            val query = prompt.trim().replaceFirst(Regex("^play\\s+", RegexOption.IGNORE_CASE), "").trim().trimEnd('.', '!')
            val opened = AssistantServices.searchMusic(context, query)
            val answer = if (opened) "Searching your music for $query." else "I couldn't open a music search for $query."
            messages.add(ChatLine("assistant", answer)); AssistantServices.saveMessage(context, "assistant", answer)
            setStatus(answer); activity.speak(answer)
        }
        lower.contains("play music") || lower == "music" || lower.contains("open music") -> {
            AssistantServices.openMusic(context)
            val answer = "Opening your music app."
            messages.add(ChatLine("assistant", answer)); AssistantServices.saveMessage(context, "assistant", answer)
            setStatus(answer); activity.speak(answer)
        }
        Regex("^(open|launch|start|show)\\s+(whatsapp|youtube|chrome|browser|internet|camera|phone|dialer|call|messages|sms|text messages|calendar|settings|contacts|gmail|email|mail|clock|alarm)[.! ]*$", RegexOption.IGNORE_CASE).matches(prompt.trim()) -> {
            val app = prompt.trim().lowercase(Locale.ROOT)
                .replace(Regex("^(open|launch|start|show)\\s+"), "")
                .trim().trimEnd('.', '!')
            val opened = AssistantServices.openExternalApp(context, app)
            val label = when (app) {
                "browser", "internet", "chrome" -> "your browser"
                "phone", "dialer", "call" -> "the phone dialer"
                "messages", "sms", "text messages" -> "Messages"
                "gmail", "email", "mail" -> "your email app"
                "clock", "alarm" -> "Clock"
                else -> app.replaceFirstChar { it.uppercase() }
            }
            val answer = if (opened) "Opening $label." else "I couldn't find an app to open for $app."
            messages.add(ChatLine("assistant", answer)); AssistantServices.saveMessage(context, "assistant", answer)
            setStatus(answer); activity.speak(answer)
        }
        Regex("^(open|launch|start|show)\\s+(.+?)[.! ]*$", RegexOption.IGNORE_CASE).matches(prompt.trim()) -> {
            val appName = prompt.trim().replace(Regex("^(open|launch|start|show)\\s+", RegexOption.IGNORE_CASE), "").trim().trimEnd('.', '!')
            val opened = AssistantServices.openInstalledApp(context, appName)
            val answer = if (opened) "Opening $appName." else "I couldn't find an installed app named $appName."
            messages.add(ChatLine("assistant", answer)); AssistantServices.saveMessage(context, "assistant", answer)
            setStatus(answer); activity.speak(answer)
        }
        lower.startsWith("remind me in ") -> {
            val match = Regex("remind me in (\\d+)\\s*(minute|minutes|min|hour|hours|hr|hrs)\\s*(?:to)?\\s*(.*)", RegexOption.IGNORE_CASE).find(prompt)
            if (match != null) {
                val amount = match.groupValues[1].toLongOrNull() ?: 0L
                val unit = match.groupValues[2].lowercase(Locale.ROOT)
                val note = match.groupValues[3].ifBlank { "Your reminder" }
                val millis = amount * if (unit.startsWith("h")) 3_600_000L else 60_000L
                val scheduled = runCatching { AssistantServices.scheduleReminder(context, note, System.currentTimeMillis() + millis) }.getOrDefault(false)
                val answer = if (scheduled) "Okay, I'll remind you about $note in ${match.groupValues[1]} ${match.groupValues[2]}." else "I couldn't schedule that exact reminder. Check Android's exact-alarm permission."
                messages.add(ChatLine("assistant", answer)); AssistantServices.saveMessage(context, "assistant", answer)
                setStatus(answer); activity.speak(answer)
            } else setStatus("Try: remind me in 10 minutes to call home.")
        }
        else -> {
            val answer = OfflineAssistant.respond(prompt)
            val needsOnlineHelp = answer.startsWith("I'm in offline mode, so I can't answer that fully yet.")
            if (needsOnlineHelp && AssistantServices.isGroqFallbackEnabled(context) &&
                AssistantServices.getGroqApiKey(context).isNotBlank()) {
                setBusy(true)
                setStatus("Checking online AI…")
                AssistantServices.askGroq(context, prompt) { result ->
                    setBusy(false)
                    result.fold(
                        onSuccess = { onlineAnswer ->
                            messages.add(ChatLine("assistant", onlineAnswer))
                            AssistantServices.saveMessage(context, "assistant", onlineAnswer)
                            setStatus("Online answer")
                            activity.speak(onlineAnswer)
                        },
                        onFailure = {
                            val fallback = OfflineAssistant.respond(prompt)
                            messages.add(ChatLine("assistant", fallback))
                            AssistantServices.saveMessage(context, "assistant", fallback)
                            setStatus("Offline fallback • online AI unavailable")
                            activity.speak(fallback)
                        }
                    )
                }
            } else {
                messages.add(ChatLine("assistant", answer))
                AssistantServices.saveMessage(context, "assistant", answer)
                setStatus("Offline assistant")
                activity.speak(answer)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Violet, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Soft, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AssistantOrb() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF34315A), Color(0xFF1B253C), Void)))
            .border(1.dp, Color(0x553D4964), CircleShape), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth(0.68f).aspectRatio(1f).border(1.dp, Color(0x668DE8E0), CircleShape).padding(14.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF9D91FF), Color(0xFF46547C), Color(0xFF172232)))), contentAlignment = Alignment.Center) {
                    Text("m", color = White, fontSize = 74.sp, fontWeight = FontWeight.Light, letterSpacing = (-5).sp)
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("HERE WHEN YOU NEED ME", color = White, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
            Text("Voice-first • Built around you", color = Soft, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ShortcutCard(icon: String, title: String, detail: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Hairline)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, color = Violet, fontSize = 22.sp)
            Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Soft, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
