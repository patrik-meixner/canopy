package com.canopy.editor

import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.sessionAttentionOf
import com.canopy.services.ClaudeSessionService
import com.canopy.services.ClaudeStatusService
import com.canopy.util.CanopyExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory

class SessionPaletteAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        CanopyExecutor.submit {
            val ranked = rankedSessions(project)
            ApplicationManager.getApplication().invokeLater { showPopup(project, ranked) }
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
        event.presentation.text = "Go to Session"
        event.presentation.description = "Search open and past sessions, waiting ones first"
    }

    private fun rankedSessions(project: Project): List<SessionDisplay> {
        val statusService = ClaudeStatusService.getInstance(project)

        return ClaudeSessionService.getInstance(project).getSessions()
            .sortedWith(
                compareBy<SessionDisplay> { attentionOf(statusService, it).rank }
                    .thenByDescending { it.modified }
            )
    }

    private fun showPopup(project: Project, sessions: List<SessionDisplay>) {
        if (sessions.isEmpty()) return

        val statusService = ClaudeStatusService.getInstance(project)
        val labels = sessions.map { session ->
            val attention = attentionOf(statusService, session)
            val marker = attention.glyph.ifEmpty { " " }
            "$marker  ${session.displayName}"
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(labels)
            .setTitle("Sessions")
            .setNamerForFiltering { it }
            .setItemChosenCallback { chosen ->
                val index = labels.indexOf(chosen)
                if (index >= 0) openSession(project, sessions[index])
            }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun attentionOf(statusService: ClaudeStatusService, session: SessionDisplay): SessionAttention =
        sessionAttentionOf(statusService.getNotifyState(session.sessionId))

    private fun openSession(project: Project, session: SessionDisplay) {
        val manager = FileEditorManager.getInstance(project)
        val existing = manager.openFiles
            .filterIsInstance<ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionId == session.sessionId }

        if (existing != null) return manager.openFile(existing, true).let { }

        manager.openFile(ClaudeSessionVirtualFile(session.tabTitle, sessionId = session.sessionId), true)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
