package com.prav1525.munch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {
    @Test
    fun stripsLeadingWakePhraseAndKeepsCommand() {
        assertEquals("play Daft Punk on YouTube", VoiceCommandParser.stripWakePhrase("Hey Jarvis, play Daft Punk on YouTube"))
        assertEquals("what time is it", VoiceCommandParser.stripWakePhrase("hey munch: what time is it"))
    }

    @Test
    fun extractsYoutubePlayRequest() {
        assertEquals("Daft Punk", VoiceCommandParser.youtubePlayQuery("play Daft Punk on YouTube"))
        assertEquals("Why This Kolaveri", VoiceCommandParser.youtubePlayQuery("Hey Jarvis, play Why This Kolaveri"))
        assertEquals("Tamil movie songs full album", VoiceCommandParser.youtubePlayQuery("please play Tamil movie songs full album"))
    }

    @Test
    fun recognizesMusicAppCommandsWithoutTreatingThemAsTrackSearches() {
        assertEquals(true, VoiceCommandParser.isMusicAppCommand("play YouTube Music"))
        assertEquals(true, VoiceCommandParser.isMusicAppCommand("Hey Jarvis, resume music"))
        assertNull(VoiceCommandParser.youtubePlayQuery("play YouTube Music"))
    }

    @Test
    fun rejectsBlankOrNonPlayCommands() {
        assertNull(VoiceCommandParser.youtubePlayQuery(""))
        assertNull(VoiceCommandParser.youtubePlayQuery("open settings"))
        assertNull(VoiceCommandParser.youtubePlayQuery("play"))
    }

    @Test
    fun preservesWordsThatContainWakeName() {
        assertEquals("munch is a word in this sentence", VoiceCommandParser.stripWakePhrase("munch is a word in this sentence"))
    }
}
