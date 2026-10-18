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
    fun parsesCommonMediaTransportCommands() {
        assertEquals("next", VoiceCommandParser.mediaCommand("Hey Munch, skip song"))
        assertEquals("previous", VoiceCommandParser.mediaCommand("Hey Jarvis, previous track"))
        assertEquals("pause", VoiceCommandParser.mediaCommand("pause music"))
        assertEquals("resume", VoiceCommandParser.mediaCommand("continue playing"))
        assertEquals("toggle", VoiceCommandParser.mediaCommand("toggle playback"))
        assertNull(VoiceCommandParser.mediaCommand("tell me a joke"))
    }

    @Test
    fun parsesOfflineJarvisDeviceSkills() {
        assertEquals("time", VoiceCommandParser.deviceCommand("Hey Jarvis, what time is it?"))
        assertEquals("date", VoiceCommandParser.deviceCommand("what's the date"))
        assertEquals("battery", VoiceCommandParser.deviceCommand("what is my battery level"))
        assertEquals("volume_up", VoiceCommandParser.deviceCommand("turn volume up"))
        assertEquals("volume_down", VoiceCommandParser.deviceCommand("quieter"))
        assertEquals("mute", VoiceCommandParser.deviceCommand("mute media"))
        assertEquals("unmute", VoiceCommandParser.deviceCommand("unmute"))
        assertEquals("help", VoiceCommandParser.deviceCommand("what can you do"))
        assertNull(VoiceCommandParser.deviceCommand("tell me a story"))
    }

    @Test
    fun preservesWordsThatContainWakeName() {
        assertEquals("munch is a word in this sentence", VoiceCommandParser.stripWakePhrase("munch is a word in this sentence"))
    }
}
