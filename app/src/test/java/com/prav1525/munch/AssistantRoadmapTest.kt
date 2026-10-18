package com.prav1525.munch

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantRoadmapTest {
    @Test
    fun roadmapStartsWithBikeAssistantAndKeepsGeneralAssistantAsFuturePhase() {
        assertEquals(AssistantPhase.BIKE_MVP, AssistantRoadmap.currentPhase)
        assertEquals(
            listOf(
                AssistantPhase.BIKE_MVP,
                AssistantPhase.RIDING_ASSISTANT,
                AssistantPhase.GENERAL_ASSISTANT
            ),
            AssistantRoadmap.phases
        )
    }

    @Test
    fun everyPhaseHasAProductDescription() {
        AssistantRoadmap.phases.forEach { phase ->
            assert(phase.title.isNotBlank())
            assert(phase.description.isNotBlank())
        }
    }
}
