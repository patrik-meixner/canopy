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
    val workingDirectory: String? = null,
    val startedAt: Instant? = null,
    val tail: TranscriptTail? = null,
    val lastPromptAt: Instant? = null,
    val isDraft: Boolean = false,
    val terminalOwner: String? = null
) {
    val isTerminal: Boolean
        get() = terminalOwner != null

    val displayName: String
        get() = name
            ?: firstPrompt.take(60).let { if (firstPrompt.length > 60) "$it..." else it }

    val tabTitle: String
        get() = name ?: worktreeName ?: firstPrompt.take(40).let { if (firstPrompt.length > 40) "$it..." else it }
}
