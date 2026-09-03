package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant

data class DraftSource(val key: String, val title: String)

fun draftRows(open: List<DraftSource>, projectPath: String, nowMillis: Long): List<SessionDisplay> =
    open.map { draft ->
        SessionDisplay(
            sessionId = draft.key,
            name = draft.title,
            firstPrompt = "",
            messageCount = 0,
            modified = Instant.ofEpochMilli(nowMillis),
            gitBranch = null,
            projectPath = projectPath,
            isDraft = true
        )
    }
