package com.prav1525.munch

import java.util.Locale

/**
 * Normalizes spoken app names and ranks visible launcher labels.
 * Based on the general matching approach used by LLMDroid's AppLookup,
 * with stricter thresholds to avoid opening unrelated apps.
 */
internal object AppNameMatcher {
    fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\b(application|app)\\b", RegexOption.IGNORE_CASE), " ")
        .filter { it.isLetterOrDigit() }

    fun score(label: String, query: String): Int {
        val app = normalize(label)
        val wanted = normalize(query)
        if (app.isBlank() || wanted.isBlank()) return 0
        if (app == wanted) return 10_000
        if (app.startsWith(wanted)) return 8_000 + wanted.length
        if (app.contains(wanted)) return 7_000 + wanted.length

        // Permit small speech/transcription differences without matching on one character.
        var qi = 0
        var run = 0
        var longestRun = 0
        for (ch in app) {
            if (qi < wanted.length && ch == wanted[qi]) {
                qi++
                run++
                if (run > longestRun) longestRun = run
            } else {
                run = 0
            }
        }
        val coverage = qi.toFloat() / wanted.length
        if (coverage < 0.75f || wanted.length < 4) return 0
        return 100 * longestRun + qi
    }
}
