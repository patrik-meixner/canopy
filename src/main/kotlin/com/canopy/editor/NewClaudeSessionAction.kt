package com.canopy.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware

class NewClaudeSessionAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        FileEditorManager.getInstance(project).openFile(ClaudeSessionVirtualFile(UNNAMED_SESSION_TITLE), true)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.icon = AllIcons.General.Add
        event.presentation.text = "New Claude Session"
        event.presentation.description = "Start a Claude session in this project"
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

const val UNNAMED_SESSION_TITLE = "New session"
