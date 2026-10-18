package com.prav1525.munch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.media.session.MediaButtonReceiver
import androidx.core.content.ContextCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.pm.ServiceInfo
import com.openwakeword.OpenWakeWord
import java.util.Locale

/**
 * User-enabled foreground listener using a real on-device openWakeWord detector.
 *
 * Use the custom "Hey Munch" model when it is bundled. Until then, use the library's
 * built-in "Hey Jarvis" model to validate offline microphone capture and wake handoff.
 * Android speech recognition remains a fallback if the detector cannot start.
 */
class WakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wakeDetector: OpenWakeWord? = null
    private var listening = false
    private var dedicatedDetectorActive = false
    private var forceSpeechFallback = false
    private var preferSystemRecognizer = false
    private var handlingWake = false
    private var awaitingCommand = false
    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Keep a media session active while the user-enabled foreground listener runs so
        // Bluetooth/headset media buttons can reach Munch even when its Activity is hidden.
        mediaSession = MediaSessionCompat(this, "MunchNeckband").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return super.onMediaButtonEvent(mediaButtonIntent)
                    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
                    return when (event.keyCode) {
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            launchVoiceFromNeckband()
                            true
                        }
                        else -> super.onMediaButtonEvent(mediaButtonIntent)
                    }
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        mainHandler.post { startRecognition() }
        return START_STICKY
    }

    private fun startRecognition() {
        if (listening || dedicatedDetectorActive || handlingWake || awaitingCommand ||
            !getSharedPreferences("munch_preferences", MODE_PRIVATE)
                .getBoolean("wake_word_enabled", false)) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Microphone permission required — open Munch to enable")
            return
        }

        if (forceSpeechFallback) {
            startSpeechRecognition()
            return
        }

        try {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
            val customModelAvailable = hasCustomWakeModel()
            val builder = OpenWakeWord.Builder(this)
                .setThreshold(WAKE_THRESHOLD)
                .setDebounceMs(3_000L)
            val detector = if (customModelAvailable) {
                builder.setModelAsset(CUSTOM_MODEL_ASSET).build()
            } else {
                builder.setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS).build()
            }
            wakeDetector = detector
            dedicatedDetectorActive = true
            updateNotification(
                if (customModelAvailable) "Offline “Hey Munch” detector active"
                else "Offline “Hey Jarvis” detector active (temporary model)"
            )
            detector.start { score ->
                // The detector callback may run on its audio thread. Serialize state changes
                // and SpeechRecognizer handoff on the main thread, and guard duplicate triggers.
                if (score >= WAKE_THRESHOLD) {
                    mainHandler.post {
                        if (dedicatedDetectorActive && !handlingWake && !awaitingCommand) {
                            handleDedicatedWake()
                        }
                    }
                }
            }
            // Do not time out a healthy detector just because no wake phrase was spoken.
            // Fall back only when initialization/start actually throws, which is handled below.
            return
        } catch (_: Exception) {
            dedicatedDetectorActive = false
            forceSpeechFallback = true
            runCatching { wakeDetector?.stop() }
            runCatching { wakeDetector?.release() }
            wakeDetector = null
            updateNotification("Wake detector unavailable — using speech fallback")
        }

        startSpeechRecognition()
    }

    private fun hasCustomWakeModel(): Boolean =
        runCatching { assets.list("")?.contains(CUSTOM_MODEL_ASSET) == true }.getOrDefault(false)

    private fun startSpeechRecognition() {
        if (listening || handlingWake ||
            !getSharedPreferences("munch_preferences", MODE_PRIVATE)
                .getBoolean("wake_word_enabled", false)) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable")
            mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
            return
        }
        try {
            recognizer?.destroy()
            // Some Android speech providers advertise on-device recognition but fail with
            // ERROR_SERVER_DISCONNECTED (11) or cannot load en-IN (13). Try on-device first,
            // then fall back to the system provider with the widely supported en-US locale.
            recognizer = if (!preferSystemRecognizer &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            recognizer?.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    if (preferSystemRecognizer) Locale.US.toLanguageTag() else Locale("en", "IN").toLanguageTag()
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Offline language packs are not installed on every device. Allow the system
                // provider to use its normal route after the on-device attempt fails.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !preferSystemRecognizer)
            }
            listening = true
            updateNotification("Speech fallback listening for “Hey Jarvis” or “Hey Munch”")
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            listening = false
            mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            updateNotification(if (awaitingCommand) "Wake phrase heard — listening for command" else "Speech fallback ready — say Hey Jarvis or Hey Munch")
        }
        override fun onBeginningOfSpeech() {
            updateNotification(if (awaitingCommand) "Command speech detected" else "Speech detected — listening for Hey Jarvis or Hey Munch")
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            // Wait for onResults/onError before restarting; restarting here races the recognizer.
            listening = false
        }
        override fun onError(error: Int) {
            listening = false
            if (awaitingCommand) {
                awaitingCommand = false
                updateNotification("Could not hear the command — listening again")
                mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
                return
            }
            val reason = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "audio capture error"
                SpeechRecognizer.ERROR_CLIENT -> "recognizer client reset"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
                SpeechRecognizer.ERROR_NETWORK -> "speech service network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "speech service timed out"
                SpeechRecognizer.ERROR_NO_MATCH -> "no speech match — retrying"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "speech service busy"
                SpeechRecognizer.ERROR_SERVER -> "speech service error"
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "speech provider disconnected"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported by this provider"
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language pack unavailable"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech detected — retrying"
                else -> "speech error $error"
            }
            // Recover from provider/language failures instead of retrying the same broken
            // on-device en-IN recognizer forever.
            if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
                preferSystemRecognizer = true
                updateNotification("Speech fallback: $reason — switching recognizer")
                mainHandler.postDelayed({ startSpeechRecognition() }, 2_500L)
            } else {
                updateNotification("Speech fallback: $reason")
                mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
            }
        }
        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (awaitingCommand) {
                awaitingCommand = false
                val recognized = phrases.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                val command = VoiceCommandParser.stripWakePhrase(recognized)
                if (command.isNotBlank()) {
                    if (!handleMediaCommandInService(command)) {
                        val launch = Intent(this@WakeWordService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("munch_wake_command", command)
                        }
                        updateNotification("Command heard — tap to run", command)
                        runCatching { startActivity(launch) }
                    }
                } else {
                    updateNotification("No command heard — listening again")
                }
                mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
                return
            }
            // Speech engines return alternatives; don't discard a valid wake phrase just because
            // the first transcript was wrong.
            val wakePhrase = phrases.firstOrNull { wakePhraseRegex().containsMatchIn(it) }
            if (wakePhrase != null) {
                handlePhrase(wakePhrase.trim())
            } else {
                updateNotification(
                    if (phrases.isEmpty()) "Speech fallback heard no words — retrying"
                    else "Speech heard but wake phrase not matched — say Hey Jarvis"
                )
            }
            mainHandler.postDelayed({ startRecognition() }, if (handlingWake) 12_000 else RESTART_DELAY_MS)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            if (awaitingCommand) return
            val phrases = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            // If the wake phrase and a complete command are already present in a partial
            // transcript, hand it off immediately rather than waiting for end-of-speech.
            // Wake on the phrase alone too; the Activity then opens normal command capture.
            // This avoids requiring the user to say the command in the same recognition window.
            val candidate = phrases.firstOrNull { wakePhraseRegex().containsMatchIn(it) }
            if (candidate != null) handlePhrase(candidate.trim())
        }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

    private fun launchVoiceFromNeckband() {
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("munch_wake_listen", true)
        }
        updateNotification("Headset button detected — opening voice input", listen = true)
        val opened = runCatching { startActivity(launch); true }.getOrDefault(false)
        if (!opened) updateNotification("Headset button detected — tap notification to talk", listen = true)
    }

    private fun handleDedicatedWake() {
        if (handlingWake || awaitingCommand) return
        dedicatedDetectorActive = false
        runCatching { wakeDetector?.stop() }
        runCatching { wakeDetector?.release() }
        wakeDetector = null
        // Capture the command in the foreground service instead of relying on Android
        // allowing a background service to open an Activity immediately after the wake phrase.
        awaitingCommand = true
        handlingWake = false
        updateNotification("Wake phrase heard — say your command")
        mainHandler.post { startSpeechRecognition() }
        mainHandler.postDelayed({
            if (awaitingCommand) {
                awaitingCommand = false
                updateNotification("No command heard — listening again")
                startRecognition()
            }
        }, COMMAND_CAPTURE_TIMEOUT_MS)
    }

    /**
     * Execute media transport commands in the foreground service. This deliberately avoids
     * launching MainActivity over the current player for next/pause/resume follow-ups.
     */
    private fun handleMediaCommandInService(rawCommand: String): Boolean {
        val command = VoiceCommandParser.stripWakePhrase(rawCommand)
            .lowercase(java.util.Locale.ROOT)
            .trim()
            .trimEnd('.', '!', '?')
        val normalized = when {
            Regex("^(?:please\\s+)?(?:next|skip)(?:\\s+(?:song|track|music))?$").matches(command) -> "next"
            Regex("^(?:please\\s+)?(?:previous|go back|last)(?:\\s+(?:song|track|music))?$").matches(command) -> "previous"
            command in setOf("pause", "pause music", "stop music") -> "pause"
            command in setOf("resume", "resume music", "continue music", "continue playing") -> "resume"
            command in setOf("play pause", "toggle playback", "toggle music") -> "toggle playback"
            else -> return false
        }
        val sent = AssistantServices.sendMediaCommand(this, normalized)
        val message = when {
            !sent -> "Could not send $normalized command to the media player"
            normalized == "next" -> "Next-track command sent"
            normalized == "previous" -> "Previous-track command sent"
            normalized == "pause" -> "Pause command sent"
            normalized == "resume" -> "Play command sent"
            else -> "Playback toggle command sent"
        }
        updateNotification(message, rawCommand)
        AssistantServices.saveMessage(this, "assistant", message)
        return true
    }

    private fun handlePhrase(phrase: String) {
        val match = Regex(".*?\\bhey[\\s,]+(?:jarvis|mun?ch)\\b[,:;.!? ]*(.*)", RegexOption.IGNORE_CASE).matchEntire(phrase)
            ?: return
        if (handlingWake) return
        handlingWake = true
        val command = match.groupValues.getOrElse(1) { "" }.trim().trim(',', '.', '!', '?')
        // Media transport commands are handled inside the foreground service so the
        // music app stays foreground instead of being covered by Munch's activity.
        if (command.isNotBlank() && handleMediaCommandInService(command)) {
            handlingWake = false
            mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
            return
        }
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (command.isNotBlank()) putExtra("munch_wake_command", command)
            else putExtra("munch_wake_listen", true)
        }
        // Android can block activity launches from background services on some versions.
        // The persistent notification remains tappable as a reliable fallback.
        updateNotification("Wake phrase heard — tap to continue", command, command.isBlank())
        runCatching { startActivity(launch) }
        mainHandler.postDelayed({ handlingWake = false }, 10_000)
        // Cancellation can suppress the normal onResults callback, so schedule recovery here too.
        mainHandler.postDelayed({ startRecognition() }, 12_000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Munch hands-free listening", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Visible status for the Hey Munch microphone listener" })
        }
    }

    private fun buildNotification(
        text: String = "Listening for “Hey Munch”",
        command: String? = null,
        listen: Boolean = false
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!command.isNullOrBlank()) openIntent.putExtra("munch_wake_command", command)
        else if (listen) openIntent.putExtra("munch_wake_listen", true)
        val openApp = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Munch hands-free")
                .setContentText(text)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Munch hands-free")
                .setContentText(text)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String, command: String? = null, listen: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, command, listen))
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        runCatching { wakeDetector?.stop() }
        runCatching { wakeDetector?.release() }
        wakeDetector = null
        dedicatedDetectorActive = false
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private fun wakePhraseRegex() = Regex("\\bhey[\\s,]+(?:jarvis|mun?ch)\\b", RegexOption.IGNORE_CASE)
        private const val CHANNEL_ID = "munch_wake_listener"
        private const val NOTIFICATION_ID = 7204
        private const val RESTART_DELAY_MS = 1_200L
                private const val COMMAND_CAPTURE_TIMEOUT_MS = 8_000L
        private const val CUSTOM_MODEL_ASSET = "hey_munch.onnx"
        private const val WAKE_THRESHOLD = 0.55f
    }
}
