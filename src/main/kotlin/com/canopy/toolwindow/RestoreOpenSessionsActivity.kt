package com.canopy.toolwindow

import com.canopy.services.ClaudeSessionService
import com.canopy.services.OpenSessionsPersistence
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Reopens the session tabs that were open when the IDE was closed.
 *
 * Restoring from the tool window meant it only happened once Canopy was expanded, so tabs came back
 * late or not at all. A startup activity also runs after the platform has restored its own editors:
 * opening one before that fails project loading on 2024.3+ with "Tab count expected to be zero".
 */
class RestoreOpenSessionsActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val persistence = OpenSessionsPersistence.getInstance(project)
        val savedIds = persistence.getAll()
        if (savedIds.isEmpty()) return

        val sessions = ClaudeSessionService.getInstance(project).getSessions().associateBy { it.sessionId }
        val terminals = persistence.getTerminals()

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            savedIds.mapNotNull(sessions::get).forEach { openClaudeSession(project, it) }
            terminals.forEach { reopenTerminal(project, it) }
        }
    }

    /**
     * A shell of its own, not the one that was there: the process it ran died with the IDE, so
     * what comes back is an empty shell in the same place under the same name.
     */
    private fun reopenTerminal(project: Project, terminal: com.canopy.services.RememberedTerminal) {
        val owner = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionId == terminal.ownerSessionId } ?: return

        com.canopy.editor.openTerminalFor(project, owner, terminal.name)
    }
}
