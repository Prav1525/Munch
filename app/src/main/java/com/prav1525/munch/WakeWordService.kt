package com.prav1525.munch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.pm.ServiceInfo
import com.openwakeword.OpenWakeWord
import java.util.Locale

/**
 * User-enabled foreground listener for "Hey Munch".
 *
 * When app/src/main/assets/hey_munch.onnx is present, a dedicated openWakeWord
 * detector listens locally. Until that trained model is bundled, speech recognition
 * remains a clearly labelled fallback and is not advertised as a true offline detector.
 */
class WakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wakeDetector: OpenWakeWord? = null
    private var listening = false
    private var dedicatedDetectorActive = false
    private var handlingWake = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        if (listening || dedicatedDetectorActive || handlingWake ||
            !getSharedPreferences("munch_preferences", MODE_PRIVATE)
                .getBoolean("wake_word_enabled", false)) return

        if (hasCustomWakeModel()) {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
                val detector = OpenWakeWord.Builder(this)
                    .setModelAsset(CUSTOM_MODEL_ASSET)
                    .setThreshold(WAKE_THRESHOLD)
                    .setDebounceMs(3_000L)
                    .build()
                wakeDetector = detector
                dedicatedDetectorActive = true
                updateNotification("Offline “Hey Munch” detector active")
                detector.start { score ->
                    if (score >= WAKE_THRESHOLD && dedicatedDetectorActive && !handlingWake) {
                        handleDedicatedWake()
                    }
                }
                return
            } catch (_: Exception) {
                dedicatedDetectorActive = false
                runCatching { wakeDetector?.release() }
                wakeDetector = null
                updateNotification("Wake model unavailable — using speech fallback")
            }
        } else {
            updateNotification("Wake model not installed — speech fallback active")
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
            recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            recognizer?.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("en", "IN").toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            listening = true
            updateNotification("Speech fallback listening for “Hey Munch”")
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            listening = false
            mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            // Wait for onResults/onError before restarting; restarting here races the recognizer.
            listening = false
        }
        override fun onError(error: Int) {
            listening = false
            mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
        }
        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            // Speech engines return alternatives; don't discard a valid wake phrase just because
            // the first transcript was wrong.
            val phrase = phrases.firstOrNull {
                Regex("\\bhey[\\s,]+mun?ch\\b", RegexOption.IGNORE_CASE).containsMatchIn(it)
            } ?: phrases.firstOrNull().orEmpty()
            handlePhrase(phrase.trim())
            mainHandler.postDelayed({ startRecognition() }, if (handlingWake) 12_000 else RESTART_DELAY_MS)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val phrases = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            // If the wake phrase and a complete command are already present in a partial
            // transcript, hand it off immediately rather than waiting for end-of-speech.
            val candidate = phrases.firstOrNull {
                val match = Regex(".*?\\bhey[\\s,]+mun?ch\\b[,:;.!? ]*(.*)", RegexOption.IGNORE_CASE)
                    .matchEntire(it)
                match != null && match.groupValues.getOrElse(1) { "" }.trim().length >= 2
            }
            if (candidate != null) handlePhrase(candidate.trim())
        }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

    private fun handleDedicatedWake() {
        if (handlingWake) return
        dedicatedDetectorActive = false
        runCatching { wakeDetector?.stop() }
        runCatching { wakeDetector?.release() }
        wakeDetector = null
        handlingWake = true
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("munch_wake_listen", true)
        }
        updateNotification("Heard “Hey Munch” — tap to continue", listen = true)
        runCatching { startActivity(launch) }
        mainHandler.postDelayed({ handlingWake = false }, 10_000)
        mainHandler.postDelayed({ startRecognition() }, 12_000)
    }

    private fun handlePhrase(phrase: String) {
        val match = Regex(".*?\\bhey[\\s,]+mun?ch\\b[,:;.!? ]*(.*)", RegexOption.IGNORE_CASE).matchEntire(phrase)
            ?: return
        if (handlingWake) return
        handlingWake = true
        val command = match.groupValues.getOrElse(1) { "" }.trim().trim(',', '.', '!', '?')
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (command.isNotBlank()) putExtra("munch_wake_command", command)
            else putExtra("munch_wake_listen", true)
        }
        // Android can block activity launches from background services on some versions.
        // The persistent notification remains tappable as a reliable fallback.
        updateNotification("Heard “Hey Munch” — tap to continue", command, command.isBlank())
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "munch_wake_listener"
        private const val NOTIFICATION_ID = 7204
        private const val RESTART_DELAY_MS = 1_200L
        private const val CUSTOM_MODEL_ASSET = "hey_munch.onnx"
        private const val WAKE_THRESHOLD = 0.55f
    }
}
