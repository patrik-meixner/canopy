package com.canopy.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware

class NewShellTerminalAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = ClaudeSessionVirtualFile("Terminal", isShellSession = true).apply {
            workingDir = project.basePath
        }

        FileEditorManager.getInstance(project).openFile(file, true)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.icon = AllIcons.General.Add
        event.presentation.text = "New Terminal"
        event.presentation.description = "Open a shell tab and pick the agent to run yourself"
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
