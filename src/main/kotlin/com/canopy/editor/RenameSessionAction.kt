package com.canopy.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile

class RenameSessionAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = sessionFileIn(event) ?: return
        val over = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return

        askInline(over, "Session name:", file.baseName) { name ->
            file.baseName = name
            file.sessionId?.let { SessionInput.send(project, it, "/rename $name\r") }
            (FileEditorManager.getInstance(project) as? com.intellij.openapi.fileEditor.ex.FileEditorManagerEx)
                ?.updateFilePresentation(file)
            project.messageBus.syncPublisher(com.canopy.services.CanopyTabsListener.TOPIC).tabsChanged()
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.text = "Rename Session"
        event.presentation.description = "Give this session a name of your own"
        event.presentation.isEnabledAndVisible = sessionFileIn(event) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun sessionFileIn(event: AnActionEvent): ClaudeSessionVirtualFile? =
        event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE) as? ClaudeSessionVirtualFile
            ?: event.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)?.file as? ClaudeSessionVirtualFile
}
