package com.canopy.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

const val CANOPY_TOOL_WINDOW = "Canopy"

class RememberActiveToolWindow(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val canopy = toolWindowManager.getToolWindow(CANOPY_TOOL_WINDOW) ?: return

        OpenSessionsPersistence.getInstance(project).wasToolWindowVisible = canopy.isVisible
    }
}
