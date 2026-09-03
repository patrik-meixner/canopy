package com.canopy.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

const val CANOPY_TOOL_WINDOW = "Canopy"

class RememberActiveToolWindow(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) = remember(toolWindowManager)

    override fun stateChanged(
        toolWindowManager: ToolWindowManager,
        changeType: ToolWindowManagerListener.ToolWindowManagerEventType
    ) = remember(toolWindowManager)

    /**
     * The IDE hides every tool window on its way out, so a state change during shutdown is not the
     * user putting the sidebar away.
     */
    private fun remember(toolWindowManager: ToolWindowManager) {
        if (CanopyShutdown.isClosing()) return
        val canopy = toolWindowManager.getToolWindow(CANOPY_TOOL_WINDOW) ?: return

        OpenSessionsPersistence.getInstance(project).wasToolWindowVisible = canopy.isVisible
    }
}
