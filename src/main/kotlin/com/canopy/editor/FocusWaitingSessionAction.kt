package com.canopy.editor

import com.canopy.model.SessionAttention
import com.canopy.model.sessionAttentionOf
import com.canopy.services.ClaudeStatusService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Jumps to the next session tab that stopped and is waiting, cycling from the current one.
 *
 * With several agents running, spotting which one went quiet is the expensive part; scanning
 * tabs by eye is what this replaces.
 */
class FocusWaitingSessionAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val manager = FileEditorManager.getInstance(project)
        val waiting = waitingFiles(project, manager)
        if (waiting.isEmpty()) return

        val current = manager.selectedFiles.firstOrNull()
        val next = waiting.firstOrNull { it != current } ?: waiting.first()

        manager.openFile(next, true)
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val count = project?.let { waitingFiles(it, FileEditorManager.getInstance(it)).size } ?: 0

        event.presentation.isEnabled = count > 0
        event.presentation.text = if (count > 0) "Go to Waiting Session ($count)" else "Go to Waiting Session"
        event.presentation.description = "Focus the next session that is blocked on you"
    }

    private fun waitingFiles(project: Project, manager: FileEditorManager): List<VirtualFile> {
        val statusService = ClaudeStatusService.getInstance(project)

        return manager.openFiles
            .filterIsInstance<ClaudeSessionVirtualFile>()
            .filter { file ->
                val sessionId = file.sessionId ?: return@filter false
                sessionAttentionOf(statusService.getNotifyState(sessionId)).isBlocking
            }
            .sortedBy { attentionRank(statusService, it) }
    }

    private fun attentionRank(statusService: ClaudeStatusService, file: ClaudeSessionVirtualFile): Int {
        val sessionId = file.sessionId ?: return SessionAttention.None.rank

        return sessionAttentionOf(statusService.getNotifyState(sessionId)).rank
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
