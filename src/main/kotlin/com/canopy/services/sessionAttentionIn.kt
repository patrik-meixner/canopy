package com.canopy.services

import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.sessionAttentionFor
import com.intellij.openapi.project.Project

fun sessionAttentionIn(project: Project, session: SessionDisplay, isRunning: Boolean): SessionAttention =
    sessionAttentionFor(
        notifyState = ClaudeStatusService.getInstance(project).getNotifyState(session.sessionId),
        isRunning = isRunning,
        tail = session.tail,
        idleForMillis = System.currentTimeMillis() - session.modified.toEpochMilli()
    )
