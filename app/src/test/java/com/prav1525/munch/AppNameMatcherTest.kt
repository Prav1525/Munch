package com.prav1525.munch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNameMatcherTest {
    @Test
    fun normalizesSpokenAppSuffixes() {
        assertEquals(
            AppNameMatcher.normalize("YouTube app"),
            AppNameMatcher.normalize("YouTube")
        )
    }

    @Test
    fun exactYouTubeLabelWins() {
        assertTrue(AppNameMatcher.score("YouTube", "youtube") > AppNameMatcher.score("YouTube Music", "youtube"))
    }

    @Test
    fun toleratesSmallTranscriptionDifferences() {
        assertTrue(AppNameMatcher.score("YouTube", "you tube") > 0)
        assertTrue(AppNameMatcher.score("WhatsApp", "whatsap") > 0)
    }

    @Test
    fun rejectsUnrelatedShortQueries() {
        assertEquals(0, AppNameMatcher.score("YouTube", "ytx"))
    }
}
