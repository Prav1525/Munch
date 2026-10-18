package com.prav1525.munch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Native actions stay independent from the UI. AI keys are user-configured and never
 * committed to source control. A production public release should proxy AI requests
 * through an authenticated backend rather than ship a client-side key.
 */
object AssistantServices {
    private val io = Executors.newSingleThreadExecutor()
    private const val PREFS = "munch_preferences"
    private const val HISTORY = "conversation_history"
    private const val API_KEY = "gemini_api_key"

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(API_KEY, key.trim()).apply()
    }

    fun hasApiKey(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(API_KEY, "").isNullOrBlank()

    fun ask(context: Context, prompt: String, callback: (Result<String>) -> Unit) {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(API_KEY, "").orEmpty()
        if (key.isBlank()) {
            callback(Result.failure(IllegalStateException("Add your Gemini API key in Settings to enable AI answers.")))
            return
        }
        io.execute {
            try {
                val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" +
                    URLEncoder.encode(key, "UTF-8"))
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("parts",
                        JSONArray().put(JSONObject().put("text", prompt)))))
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream.bufferedReader().use { it.readText() }
                connection.disconnect()
                if (code !in 200..299) throw IllegalStateException("AI request failed (HTTP $code). Check the API key and connection.")
                val json = JSONObject(raw)
                val answer = json.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                    .orEmpty()
                if (answer.isBlank()) throw IllegalStateException("The AI service returned an empty answer.")
                callback(Result.success(answer))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    fun saveMessage(context: Context, speaker: String, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val history = runCatching { JSONArray(prefs.getString(HISTORY, "[]")) }.getOrElse { JSONArray() }
        history.put(JSONObject().put("speaker", speaker).put("message", message).put("time", System.currentTimeMillis()))
        while (history.length() > 100) history.remove(0)
        prefs.edit().putString(HISTORY, history.toString()).apply()
    }

    fun readHistory(context: Context): List<Pair<String, String>> {
        val history = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HISTORY, "[]"))
        }.getOrElse { JSONArray() }
        return (0 until history.length()).mapNotNull { i ->
            history.optJSONObject(i)?.let { it.optString("speaker") to it.optString("message") }
        }
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(HISTORY).apply()
    }

    fun openMaps(context: Context, destination: String) {
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openMusic(context: Context) {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
    }

    fun scheduleReminder(context: Context, message: String, triggerAtMillis: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return false
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("message", message)
        val pending = PendingIntent.getBroadcast(context, (triggerAtMillis % Int.MAX_VALUE).toInt(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        return true
    }
}
