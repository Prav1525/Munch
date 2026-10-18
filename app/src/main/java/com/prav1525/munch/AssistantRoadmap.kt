package com.prav1525.munch

/**
 * Capabilities stay modular so everyday assistance is the core product,
 * while riding, music, navigation, and other contexts remain optional modules.
 */
enum class AssistantPhase(val title: String, val description: String) {
    PERSONAL_ASSISTANT_FOUNDATION(
        title = "Personal assistant foundation",
        description = "A clear conversation experience, voice input, and a personal assistant shell."
    ),
    CONNECTED_CAPABILITIES(
        title = "Connected capabilities",
        description = "Add optional music, navigation, reminders, and device integrations."
    ),
    SMARTER_ASSISTANT(
        title = "Smarter assistant",
        description = "Improve context, useful offline features, personalization, and background behavior within Android limits."
    )
}

object AssistantRoadmap {
    val phases: List<AssistantPhase> = AssistantPhase.entries.toList()
    val currentPhase: AssistantPhase = AssistantPhase.PERSONAL_ASSISTANT_FOUNDATION
}
