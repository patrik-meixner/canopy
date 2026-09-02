package com.canopy.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

const val CANOPY_TOOL_WINDOW = "Canopy"

/**
 * Remembers whether Canopy was the tool window on screen.
 *
 * Several tool windows share the left stripe and only one of them shows, so a restart always came
 * back to Project - the session list you left open was two clicks away every morning.
 */
class RememberActiveToolWindow(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val canopy = toolWindowManager.getToolWindow(CANOPY_TOOL_WINDOW) ?: return

        OpenSessionsPersistence.getInstance(project).wasToolWindowVisible = canopy.isVisible
    }
}
