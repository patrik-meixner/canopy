package com.canopy.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * Opens the message history beside a session's terminal.
 *
 * The only way in used to be a button on the session toolbar, which is exactly the row people turn
 * off once they read the same numbers from Claude Code's own status line.
 */
class ToggleMessageHistoryAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        selectedSessionEditor(project)?.toggleMessageHistory()
    }

    override fun update(event: AnActionEvent) {
        val project = event.project

        event.presentation.isEnabled = project != null && selectedSessionEditor(project) != null
    }

    private fun selectedSessionEditor(project: Project): ClaudeSessionEditor? {
        val manager = FileEditorManager.getInstance(project)
        val session = manager.selectedFiles.firstOrNull { it is ClaudeSessionVirtualFile } ?: return null

        return manager.getEditors(session).filterIsInstance<ClaudeSessionEditor>().firstOrNull()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
