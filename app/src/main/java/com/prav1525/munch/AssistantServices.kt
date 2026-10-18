package com.prav1525.munch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private const val GROQ_API_KEY = "groq_api_key"
    private const val GROQ_FALLBACK_ENABLED = "groq_fallback_enabled"

    fun getGroqApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(GROQ_API_KEY, "").orEmpty()

    fun saveGroqApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(GROQ_API_KEY, key.trim()).apply()
    }

    fun isGroqFallbackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(GROQ_FALLBACK_ENABLED, false)

    fun setGroqFallbackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(GROQ_FALLBACK_ENABLED, enabled).apply()
    }

    /** Calls Groq only when explicitly enabled and configured. Callback is always delivered on main. */
    fun askGroq(context: Context, prompt: String, callback: (Result<String>) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString(GROQ_API_KEY, "").orEmpty()
        if (!prefs.getBoolean(GROQ_FALLBACK_ENABLED, false) || key.isBlank()) {
            callback(Result.failure(IllegalStateException("Online fallback is disabled or no Groq API key is configured.")))
            return
        }
        io.execute {
            val result = runCatching {
                val endpoint = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $key")
                }
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are Munch, a concise personal assistant. Answer naturally, usually in one short sentence. Avoid filler. Do not claim to perform actions you have not performed."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
                val body = JSONObject()
                    .put("model", "openai/gpt-oss-20b")
                    .put("messages", messages)
                    .put("max_tokens", 64)
                    .put("temperature", 0.3)
                    .toString()
                try {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
                        throw IllegalStateException("Groq API error (HTTP $code)" + if (detail.isNotBlank()) ": ${detail.take(180)}" else ".")
                    }
                    val answer = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty().trim()
                    if (answer.isBlank()) throw IllegalStateException("Groq returned an empty answer.")
                    answer
                } finally {
                    connection.disconnect()
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }
    }

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
                // Prefer the current stable Flash model. Try alternatives if a model-specific
                // 404 indicates that this model is unavailable to the configured project.
                val models = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite")
                var answer: String? = null
                var lastError: String? = null

                for (model in models) {
                    val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15000
                        readTimeout = 30000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("x-goog-api-key", key)
                    }
                    val body = JSONObject()
                        .put("contents", JSONArray().put(JSONObject().put("parts",
                            JSONArray().put(JSONObject().put("text", "You are Munch, a helpful personal assistant. Reply naturally and concisely, usually in one short sentence. Give more detail only when the question needs it or the user asks for an explanation. Avoid filler, repeated confirmations, long introductions, and unnecessary lists. For simple requests, answer directly. Keep safety-critical information complete.\n\nUser: " + prompt)))))
                        .toString()
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    connection.disconnect()

                    if (code in 200..299) {
                        val json = JSONObject(raw)
                        answer = json.optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                            .orEmpty()
                        if (answer.isNullOrBlank()) {
                            lastError = "The AI service returned an empty answer."
                            break
                        }
                        break
                    }

                    val detail = runCatching {
                        JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    lastError = "Gemini API error (HTTP $code)" + if (detail.isNotBlank()) ": ${detail.take(240)}" else "."

                    // Do not mask invalid-key, permission, or quota errors by retrying them.
                    if (code == 404) continue
                    throw IllegalStateException(lastError)
                }

                if (!answer.isNullOrBlank()) callback(Result.success(answer!!))
                else throw IllegalStateException(lastError ?: "No supported Gemini model was available. Check your API key and project access.")
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

    /** Open common installed apps through Android intents. This launches apps; it does not grant
     * unrestricted control over them. Sensitive actions remain in the target app for confirmation. */
    fun openExternalApp(context: Context, app: String): Boolean {
        val normalized = app.trim().lowercase()
        val intent = when (normalized) {
            "whatsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))
            "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            "browser", "chrome", "internet" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            "camera" -> Intent("android.media.action.IMAGE_CAPTURE")
            "phone", "dialer", "call" -> Intent(Intent.ACTION_DIAL)
            "messages", "sms", "text messages" -> Intent(Intent.ACTION_VIEW, Uri.parse("smsto:"))
            "calendar" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
            "contacts" -> Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))
            "gmail", "email", "mail" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            "clock", "alarm" -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            else -> return false
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            // If a deep link cannot be handled, use the generic app launcher where possible.
            val packageName = when (normalized) {
                "whatsapp" -> "com.whatsapp"
                "youtube" -> "com.google.android.youtube"
                "chrome", "browser", "internet" -> "com.android.chrome"
                "gmail", "email", "mail" -> "com.google.android.gm"
                else -> null
            }
            val launch = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?: return false
            runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
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
