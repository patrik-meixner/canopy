package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant

/** A shell tab that belongs to a session, identified by the key its own tab already has. */
data class TerminalSource(val key: String, val title: String, val ownerRowId: String)

/**
 * A terminal whose session is not on the list is left out rather than floated to the top: its tab
 * is still open and still reachable, and a row with no session above it says nothing about what it
 * belongs to.
 */
fun withTerminalRows(
    rows: List<SessionDisplay>,
    terminals: List<TerminalSource>,
    projectPath: String,
    nowMillis: Long
): List<SessionDisplay> {
    if (terminals.isEmpty()) return rows

    val byOwner = terminals.groupBy { it.ownerRowId }

    return rows.flatMap { row ->
        listOf(row) + byOwner[row.sessionId].orEmpty().map { terminalRow(it, projectPath, nowMillis) }
    }
}

private fun terminalRow(terminal: TerminalSource, projectPath: String, nowMillis: Long) = SessionDisplay(
    sessionId = terminal.key,
    name = terminal.title,
    firstPrompt = "",
    messageCount = 0,
    modified = Instant.ofEpochMilli(nowMillis),
    gitBranch = null,
    projectPath = projectPath,
    terminalOwner = terminal.ownerRowId
)
