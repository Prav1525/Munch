package com.prav1525.munch

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, deterministic assistant that works without a network connection or API key.
 * It handles common local requests and provides an honest offline fallback.
 */
object OfflineAssistant {
    fun respond(input: String, nowMillis: Long = System.currentTimeMillis()): String {
        val text = input.trim()
        val lower = text.lowercase(Locale.ROOT)
        if (text.isBlank()) return "What would you like help with?"

        return when {
            lower.matches(Regex("^(hi|hello|hey|hey munch|good morning|good afternoon|good evening)[!. ]*$")) ->
                "Hello! What can I help you with?"
            lower.contains("what time") || lower == "time" || lower.contains("current time") ->
                "It's ${DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(nowMillis))}."
            lower.contains("what date") || lower.contains("today's date") || lower == "date" || lower.contains("what day is it") ->
                "Today is ${DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(Date(nowMillis))}."
            lower.contains("what can you do") || lower == "help" || lower.contains("your features") ->
                "I work offline for music-app launch, Maps handoff, reminders, voice replies, and simple questions about time and date."
            lower.contains("are you online") || lower.contains("are you offline") || lower.contains("do you need internet") ->
                "I'm set up for offline responses; Maps and some phone services may still need internet."
            lower.contains("thank") ->
                "You're welcome!"
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I'm Munch, your personal assistant."
            lower.contains("how are you") ->
                "I'm ready to help. What would you like to do?"
            lower.contains("joke") ->
                "Why did the phone wear glasses? It lost its contacts."
            else ->
                "I'm in offline mode, so I can't answer that fully yet. Try asking for the time, date, help, music, navigation, or a reminder."
        }
    }
}
