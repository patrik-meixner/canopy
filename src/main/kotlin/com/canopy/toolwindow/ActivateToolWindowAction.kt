package com.canopy.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

abstract class ActivateToolWindowAction(private val id: String) : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        ToolWindowManager.getInstance(project).getToolWindow(id)?.activate(null)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class ActivateCanopyAction : ActivateToolWindowAction("Canopy")

class ActivateSessionAction : ActivateToolWindowAction(SessionWindow.ID)
