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
        return when (VoiceCommandParser.deviceCommand(rawCommand)) {
            "time" ->
                "It is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
            "date" ->
                "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
            "battery" ->
                batteryStatus(context)
            "volume_up" -> adjustVolume(context, AudioManager.ADJUST_RAISE)
            "volume_down" -> adjustVolume(context, AudioManager.ADJUST_LOWER)
            "mute" -> adjustVolume(context, AudioManager.ADJUST_MUTE)
            "unmute" -> adjustVolume(context, AudioManager.ADJUST_UNMUTE)
            "help" ->
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
