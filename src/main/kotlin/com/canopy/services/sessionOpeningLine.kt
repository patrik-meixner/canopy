package com.canopy.services

/**
 * Claude Code gives a session a new id when it resumes one, and the transcript under that id opens
 * with the agent already working, so it holds no prompt of its own.
 */
internal fun sessionOpeningLine(firstPrompt: String?, firstAgentText: String?): String? =
    firstPrompt?.takeIf { it.isNotBlank() } ?: firstAgentText?.takeIf { it.isNotBlank() }
