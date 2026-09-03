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
     * The IDE lays the sidebar out itself at both ends of a session, and neither is the user: it
     * opens its own default before the restore runs, and hides everything on the way out.
     */
    private fun remember(toolWindowManager: ToolWindowManager) {
        val persistence = OpenSessionsPersistence.getInstance(project)
        if (!persistence.isTrackingToolWindow || CanopyShutdown.isClosing()) return
        val canopy = toolWindowManager.getToolWindow(CANOPY_TOOL_WINDOW) ?: return

        persistence.wasToolWindowVisible = canopy.isVisible
    }
}
