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
        val savedIds = OpenSessionsPersistence.getInstance(project).getAll()
        if (savedIds.isEmpty()) return

        val sessions = ClaudeSessionService.getInstance(project).getSessions().associateBy { it.sessionId }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            savedIds.mapNotNull(sessions::get).forEach { openClaudeSession(project, it) }
        }
    }
}
