package com.canopy.services

import com.canopy.editor.ClaudeSessionVirtualFile
import com.canopy.editor.SessionInput
import com.canopy.model.SessionAttention
import com.canopy.model.sessionAttentionOf
import com.canopy.settings.CanopySettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/**
 * Tells you when an agent stops and waits, so the tool window does not have to be watched.
 *
 * Only a transition into a blocking state notifies, and never for the session already on screen —
 * being told about the tab you are reading is noise, and repeat balloons for one prompt worse.
 */
object SessionAttentionNotifier {

    private const val GROUP = "Canopy Session Attention"

    fun onNotifyStateChanged(project: Project, sessionId: String, notifyState: String?) {
        if (!wanted(sessionAttentionOf(notifyState))) return

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || isOnScreen(project, sessionId)) return@invokeLater

            notify(project, sessionId, notifyState)
        }
    }

    /**
     * A permission prompt genuinely blocks the agent. Finishing a turn does not: it happens between
     * every batch of tool calls, so notifying on it means a balloon every few seconds.
     */
    private fun wanted(attention: SessionAttention): Boolean {
        val settings = CanopySettings.getInstance().state

        return when (attention) {
            SessionAttention.NeedsPermission -> settings.notifyWhenBlocked
            SessionAttention.WaitingForInput -> settings.notifyWhenWaiting
            else -> false
        }
    }

    private fun isOnScreen(project: Project, sessionId: String): Boolean {
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

        return (selected as? ClaudeSessionVirtualFile)?.sessionId == sessionId
    }

    private fun notify(project: Project, sessionId: String, notifyState: String?) {
        val name = ClaudeSessionService.getInstance(project).cachedDisplayName(sessionId) ?: "A session"
        val what = if (notifyState == "permission_prompt") "needs permission" else "is waiting for you"

        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP)
            .createNotification("$name $what", NotificationType.INFORMATION)
            .addAction(object : DumbAwareAction("Open Session") {
                override fun actionPerformed(event: AnActionEvent) = openSession(project, sessionId)
            })
            .addAction(object : DumbAwareAction("Approve") {
                override fun actionPerformed(event: AnActionEvent) = SessionInput.approve(project, sessionId)
            })
            .addAction(object : DumbAwareAction("Reply\u2026") {
                override fun actionPerformed(event: AnActionEvent) = SessionInput.promptAndReply(project, sessionId, name)
            })
            .notify(project)
    }

    private fun openSession(project: Project, sessionId: String) {
        val manager = FileEditorManager.getInstance(project)
        val existing = manager.openFiles
            .filterIsInstance<ClaudeSessionVirtualFile>()
            .firstOrNull { it.sessionId == sessionId }
            ?: return

        manager.openFile(existing, true)
    }
}
