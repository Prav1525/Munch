package com.prav1525.munch

import java.util.Locale

/**
 * Pure parsing for hands-free voice commands. Kept separate from Android services so
 * wake-prefix handling and common media intents can be covered by fast unit tests.
 */
object VoiceCommandParser {
    private val wakePrefix = Regex(
        "^\\s*hey[\\s,]+(?:jarvis|mun?ch)\\b[,:;.!?\\s-]*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val youtubePlay = Regex(
        "^(?:please\\s+)?play\\s+(.+?)(?:\\s+on\\s+youtube)?$",
        RegexOption.IGNORE_CASE
    )

    /** Returns the text after a leading supported wake phrase, or the original text if absent. */
    fun stripWakePhrase(raw: String): String {
        val trimmed = raw.trim()
        val match = wakePrefix.matchEntire(trimmed)
        return (match?.groupValues?.getOrNull(1) ?: trimmed)
            .trim()
            .trim(',', '.', '!', '?', ':', ';', '-')
            .trim()
    }

    /** Recognizes app-level music commands separately from requests for a specific track. */
    fun isMusicAppCommand(raw: String): Boolean {
        val command = stripWakePhrase(raw)
            .lowercase(Locale.ROOT)
            .replace(Regex("[.!?]+$"), "")
            .trim()
        return command in setOf(
            "play music", "resume music", "continue music",
            "play youtube music", "open youtube music", "launch youtube music",
            "open yt music", "play yt music", "resume youtube music"
        )
    }

    /** Extracts a non-empty play request for YouTube; unrelated commands return null. */
    fun youtubePlayQuery(raw: String): String? {
        val command = stripWakePhrase(raw)
        if (isMusicAppCommand(command)) return null
        val match = youtubePlay.matchEntire(command) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
