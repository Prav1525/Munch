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
import java.util.Locale

/**
 * User-enabled foreground listener for the "Hey Munch" phrase.
 *
 * Android's general speech recognizer is not a dedicated keyword spotter; on-device
 * recognition is preferred when available, but a device may fall back to network speech.
 */
class WakeWordService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
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
        if (listening || !getSharedPreferences("munch_preferences", MODE_PRIVATE)
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            listening = true
            updateNotification("Listening for “Hey Munch”")
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
            listening = false
            mainHandler.postDelayed({ startRecognition() }, 400)
        }
        override fun onError(error: Int) {
            listening = false
            mainHandler.postDelayed({ startRecognition() }, RESTART_DELAY_MS)
        }
        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val phrase = phrases.firstOrNull().orEmpty().trim()
            handlePhrase(phrase)
            mainHandler.postDelayed({ startRecognition() }, if (handlingWake) 12_000 else 400)
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    }

    private fun handlePhrase(phrase: String) {
        val match = Regex(".*?\\bhey\\s+munch\\b[,:;.!? ]*(.*)", RegexOption.IGNORE_CASE).matchEntire(phrase)
            ?: return
        if (handlingWake) return
        handlingWake = true
        val command = match.groupValues.getOrElse(1) { "" }.trim().trim(',', '.', '!', '?')
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (command.isNotBlank()) putExtra("munch_wake_command", command)
            else putExtra("munch_wake_listen", true)
        }
        runCatching { startActivity(launch) }
        mainHandler.postDelayed({ handlingWake = false }, 10_000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Munch hands-free listening", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Visible status for the Hey Munch microphone listener" })
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Munch hands-free")
                .setContentText("Listening for “Hey Munch”")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Munch hands-free")
                .setContentText("Listening for “Hey Munch”")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification().also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Notification content is recreated by buildNotification; the channel stays low-noise.
            }
        })
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "munch_wake_listener"
        private const val NOTIFICATION_ID = 7204
        private const val RESTART_DELAY_MS = 1_200L
    }
}
