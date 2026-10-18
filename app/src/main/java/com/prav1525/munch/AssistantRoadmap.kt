package com.prav1525.munch

/**
 * Product phases are deliberately modeled separately from UI so the bike-assistant
 * foundation can grow into a general assistant without rewriting the app shell.
 */
enum class AssistantPhase(val title: String, val description: String) {
    BIKE_MVP(
        title = "Bike assistant",
        description = "Hands-free voice, music controls, navigation, and Bluetooth audio."
    ),
    RIDING_ASSISTANT(
        title = "Smarter riding assistant",
        description = "More reliable commands, practical offline support, and background operation."
    ),
    GENERAL_ASSISTANT(
        title = "General assistant",
        description = "Everyday questions, tasks, and additional assistant modules."
    )
}

object AssistantRoadmap {
    val phases: List<AssistantPhase> = AssistantPhase.entries.toList()
    val currentPhase: AssistantPhase = AssistantPhase.BIKE_MVP
}
