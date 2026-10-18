package com.prav1525.munch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRoadmapTest {
    @Test
    fun roadmapStartsWithPersonalAssistantFoundation() {
        assertEquals(AssistantPhase.PERSONAL_ASSISTANT_FOUNDATION, AssistantRoadmap.currentPhase)
        assertEquals(
            listOf(
                AssistantPhase.PERSONAL_ASSISTANT_FOUNDATION,
                AssistantPhase.CONNECTED_CAPABILITIES,
                AssistantPhase.SMARTER_ASSISTANT
            ),
            AssistantRoadmap.phases
        )
    }

    @Test
    fun phaseDescriptionsArePresentAndRidingIsAnOptionalCapability() {
        AssistantRoadmap.phases.forEach { phase ->
            assertTrue(phase.title.isNotBlank())
            assertTrue(phase.description.isNotBlank())
        }
        assertFalse(AssistantRoadmap.phases.first().description.contains("bike", ignoreCase = true))
    }
}
