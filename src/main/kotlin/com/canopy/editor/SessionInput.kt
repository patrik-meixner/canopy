package com.canopy.editor

import com.canopy.services.SessionRuntime
import com.canopy.services.SessionRuntimeService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Answers a session without putting it on screen.
 *
 * A permission prompt blocks the agent until someone presses a key, and walking to the tab to
 * press one is the whole cost. It writes into a live PTY, it does not resume anything.
 */
object SessionInput {

    private const val ACCEPT_DEFAULT = "\r"

    fun canSend(project: Project, sessionId: String): Boolean = runtimeFor(project, sessionId) != null

    /** Text composed elsewhere, typed into the session as if you had typed it. */
    fun send(project: Project, sessionId: String, text: String) {
        val runtime = runtimeFor(project, sessionId)
        if (runtime == null) {
            return Messages.showInfoMessage(
                project,
                "Start the session first: a note is typed into its terminal.",
                "Session Is Not Running"
            )
        }

        runtime.sendInput(text)
    }

    fun approve(project: Project, sessionId: String) {
        runtimeFor(project, sessionId)?.sendInput(ACCEPT_DEFAULT)
    }

    fun promptAndReply(project: Project, sessionId: String, sessionName: String, over: java.awt.Component?) {
        val runtime = runtimeFor(project, sessionId) ?: return

        askInline(over, "Reply to $sessionName:") { typed ->
            replyPayload(typed)?.let(runtime::sendInput)
        }
    }

    private fun runtimeFor(project: Project, sessionId: String): SessionRuntime? =
        SessionRuntimeService.getInstance(project).existing(sessionId)
}

/** Blank stays unsent: an empty reply would submit the prompt's default answer by accident. */
internal fun replyPayload(typed: String): String? {
    val trimmed = typed.trim()

    return if (trimmed.isEmpty()) null else "$trimmed\r"
}
