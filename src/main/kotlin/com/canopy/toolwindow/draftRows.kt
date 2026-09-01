package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant

/** A session tab that is open but has not started, identified by the key its tab already has. */
data class DraftSource(val key: String, val title: String)

/**
 * The rows for sessions that exist as a tab and nowhere else yet.
 *
 * Starting one used to leave the list showing the session you were on before, because a session
 * only becomes a row once it has written a transcript - which does not happen until you have said
 * something. The tab is enough to stand for it, and the row goes when the tab does.
 */
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
