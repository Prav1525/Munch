package com.prav1525.munch

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fast, offline device skills that do not need an LLM or a foreground Activity.
 * Returns null when the utterance is not a supported device command.
 */
object DeviceCommandActions {
    fun execute(context: Context, rawCommand: String): String? {
        val command = VoiceCommandParser.stripWakePhrase(rawCommand)
            .lowercase(Locale.ROOT)
            .replace(Regex("[.!?]+$"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

        return when (command) {
            "what time is it", "tell me the time", "current time", "time please" ->
                "It is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
            "what is the date", "what's the date", "what date is it", "today's date", "current date" ->
                "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
            "battery", "battery status", "battery level", "what is my battery", "what's my battery",
            "how much battery", "what is my battery level", "what percentage is my battery" ->
                batteryStatus(context)
            "volume up", "increase volume", "turn volume up", "louder" -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            "volume down", "decrease volume", "turn volume down", "quieter" -> adjustVolume(context, AudioManager.ADJUST_LOWER)
            "mute", "mute volume", "mute media" -> adjustVolume(context, AudioManager.ADJUST_MUTE)
            "unmute", "unmute volume", "unmute media" -> adjustVolume(context, AudioManager.ADJUST_UNMUTE)
            "what can you do", "help", "list your commands", "what commands do you know" ->
                "I can control music playback, search Spotify and YouTube Music, open apps, set reminders, navigate with Maps, tell the time and date, check battery, and adjust media volume."
            else -> null
        }
    }

    private fun batteryStatus(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilterCompat.batteryChanged())
            ?: return "I couldn't read the battery status right now."
        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", -1)
        if (level < 0 || scale <= 0) return "I couldn't read the battery percentage."
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val status = intent.getIntExtra("status", -1)
        val charging = status == 2 || status == 5
        return "Your battery is $percent percent" + if (charging) " and charging." else "."
    }

    private fun adjustVolume(context: Context, direction: Int): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return runCatching {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            when (direction) {
                AudioManager.ADJUST_RAISE -> "Media volume increased."
                AudioManager.ADJUST_LOWER -> "Media volume decreased."
                AudioManager.ADJUST_MUTE -> "Media volume muted."
                else -> "Media volume unmuted."
            }
        }.getOrElse { "I couldn't change the media volume on this device." }
    }

    private object IntentFilterCompat {
        fun batteryChanged() = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    }
}
