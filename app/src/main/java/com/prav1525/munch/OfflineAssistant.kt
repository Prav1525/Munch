package com.prav1525.munch

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, deterministic assistant that works without a network connection or API key.
 * Known short requests can be answered without invoking the language model.
 */
object OfflineAssistant {
    fun respondInstant(input: String, nowMillis: Long = System.currentTimeMillis()): String? {
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
                "I can open music, start navigation, set reminders, and answer simple questions offline."
            lower.contains("are you online") || lower.contains("are you offline") || lower.contains("do you need internet") ->
                "I can answer offline. Maps and some phone services may still need internet."
            lower.matches(Regex("^(thanks|thank you|thanks munch|thank you munch)[!. ]*$")) || lower.startsWith("thank ") ->
                "You're welcome!"
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I'm Munch, your personal assistant."
            lower.contains("how are you") ->
                "I'm ready to help."
            lower.contains("tell me a joke") || lower == "joke" || lower.contains("make me laugh") ->
                "Why did the phone wear glasses? It lost its contacts."
            else -> null
        }
    }

    fun respond(input: String, nowMillis: Long = System.currentTimeMillis()): String =
        respondInstant(input, nowMillis)
            ?: "I'm in offline mode, so I can't answer that fully yet. Try asking for the time, date, help, music, navigation, or a reminder."
}
