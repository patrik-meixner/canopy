package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant

data class DraftSource(val key: String, val title: String, val reported: String? = null)

/**
 * A tab keeps its own row until the session it started shows up in the list, because until then
 * nothing else on screen stands for it.
 */
fun draftRows(
    open: List<DraftSource>,
    listed: Set<String>,
    projectPath: String,
    nowMillis: Long
): List<SessionDisplay> =
    open.filterNot { it.reported in listed }.map { draft ->
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
