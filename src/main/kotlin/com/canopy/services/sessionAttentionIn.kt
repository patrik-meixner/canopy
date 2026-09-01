package com.canopy.services

import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.sessionAttentionFor
import com.intellij.openapi.project.Project

/**
 * What a session wants, from the two places that know: the CLI's notify state and its transcript.
 *
 * Three surfaces asked this and each assembled the same four arguments by hand, so the tab, the row
 * and the review header could disagree about one session.
 */
fun sessionAttentionIn(project: Project, session: SessionDisplay, isRunning: Boolean): SessionAttention =
    sessionAttentionFor(
        notifyState = ClaudeStatusService.getInstance(project).getNotifyState(session.sessionId),
        isRunning = isRunning,
        tail = session.tail,
        idleForMillis = System.currentTimeMillis() - session.modified.toEpochMilli()
    )
