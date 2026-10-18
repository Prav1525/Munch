package com.prav1525.munch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAssistantTest {
    @Test
    fun greetingWorksWithoutNetwork() {
        assertEquals("Hello! What can I help you with?", OfflineAssistant.respond("Hey Munch"))
    }

    @Test
    fun unknownQuestionExplainsOfflineLimit() {
        val answer = OfflineAssistant.respond("Explain quantum computing")
        assertTrue(answer.contains("offline mode"))
        assertTrue(answer.length < 180)
    }

    @Test
    fun helpListsLocalFeatures() {
        val answer = OfflineAssistant.respond("What can you do?")
        assertTrue(answer.contains("reminders"))
        assertTrue(answer.contains("music"))
    }

    @Test
    fun timeQuestionReturnsAResponse() {
        assertTrue(OfflineAssistant.respond("What time is it?", 0L).startsWith("It's "))
    }
}
