package com.canopy.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Answers a session without putting it on screen.
 *
 * A permission prompt blocks the agent until someone presses a key, and walking to the tab to
 * press one is the whole cost. The tab still has to exist: this writes into a live PTY, it does
 * not resume anything.
 */
object SessionInput {

    private const val ACCEPT_DEFAULT = "\r"

    fun canSend(project: Project, sessionId: String): Boolean = editorFor(project, sessionId) != null

    /** Text composed elsewhere, typed into the session as if you had typed it. */
    fun send(project: Project, sessionId: String, text: String) {
        val editor = editorFor(project, sessionId)
        if (editor == null) {
            return Messages.showInfoMessage(
                project,
                "Open the session's tab first: a note is typed into its terminal.",
                "Session Is Not Open"
            )
        }

        editor.sendToTerminal(text)
    }

    fun approve(project: Project, sessionId: String) {
        editorFor(project, sessionId)?.sendToTerminal(ACCEPT_DEFAULT)
    }

    fun promptAndReply(project: Project, sessionId: String, sessionName: String) {
        val editor = editorFor(project, sessionId) ?: return
        val typed = Messages.showInputDialog(project, "Message", "Reply to $sessionName", null) ?: return
        val payload = replyPayload(typed) ?: return

        editor.sendToTerminal(payload)
    }

    private fun editorFor(project: Project, sessionId: String): ClaudeSessionEditor? =
        FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<ClaudeSessionEditor>()
            .firstOrNull { it.file.sessionId == sessionId }
}

/** Blank stays unsent: an empty reply would submit the prompt's default answer by accident. */
internal fun replyPayload(typed: String): String? {
    val trimmed = typed.trim()

    return if (trimmed.isEmpty()) null else "$trimmed\r"
}
