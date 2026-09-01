package com.canopy.model

import java.time.Instant

data class SessionDisplay(
    val sessionId: String,
    val name: String?,
    val firstPrompt: String,
    val messageCount: Int,
    val modified: Instant,
    val gitBranch: String?,
    val projectPath: String,
    val worktreeName: String? = null,
    val touchedRoots: Map<String, Int> = emptyMap(),
    val startedAt: Instant? = null,
    val tail: TranscriptTail? = null,
    /** When you last said something here. The list orders on this so an agent writing cannot move it. */
    val lastPromptAt: Instant? = null,
    /** A tab that is open but has not started: it has no transcript and nothing to act on yet. */
    val isDraft: Boolean = false
) {
    val displayName: String
        get() = name
            ?: firstPrompt.take(60).let { if (firstPrompt.length > 60) "$it..." else it }

    val tabTitle: String
        get() = name ?: worktreeName ?: firstPrompt.take(40).let { if (firstPrompt.length > 40) "$it..." else it }
}
