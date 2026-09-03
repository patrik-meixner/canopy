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
        if (persistence.wasToolWindowVisible) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) showCanopy(project)
            }
        }

        val savedIds = persistence.getAll()
        if (savedIds.isEmpty()) return

        val mode = com.canopy.settings.CanopySettings.getInstance().state.restoreSessions
        if (mode == com.canopy.settings.SessionRestore.None) return

        val sessions = ClaudeSessionService.getInstance(project).getSessions().associateBy { it.sessionId }
        val plan = restorePlan(savedIds.mapNotNull(sessions::get), mode)
        val staged = plan.staged ?: return
        val terminals = persistence.getTerminals().filter { it.ownerSessionId == staged.sessionId }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            openClaudeSession(project, staged)
            terminals.forEach { reopenTerminal(project, it) }
            focusFirst(project, staged)
            plan.detached.forEachIndexed { index, session -> restoreDetachedLater(project, session, index + 1) }
        }
    }

    private fun restoreDetachedLater(project: Project, session: com.canopy.model.SessionDisplay, position: Int) {
        com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().schedule({
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                com.canopy.services.startSessionDetached(project, session, workingDirectoryOf(project, session))
            }
        }, position * RESTORE_STAGGER_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun workingDirectoryOf(project: Project, session: com.canopy.model.SessionDisplay): String? {
        val worktree = session.worktreeName ?: return session.workingDirectory
        val base = project.basePath ?: return session.workingDirectory

        return com.canopy.util.ClaudePathEncoder.worktreeAbsolutePath(base, worktree)
    }

    private fun showCanopy(project: Project) {
        com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            .getToolWindow(com.canopy.services.CANOPY_TOOL_WINDOW)
            ?.show()
    }

    private fun focusFirst(project: Project, session: com.canopy.model.SessionDisplay) {
        val file = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionId == session.sessionId } ?: return

        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun reopenTerminal(project: Project, terminal: com.canopy.services.RememberedTerminal) {
        val owner = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionId == terminal.ownerSessionId } ?: return

        com.canopy.editor.openTerminalFor(project, owner, terminal.name)
    }
}

private const val RESTORE_STAGGER_MS = 1_000L
