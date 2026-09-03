package com.canopy.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

object SessionWindow {

    const val ID = "Session"

    fun activateDetail(project: Project) {
        val window = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return

        if (!window.isVisible) return

        window.activate {
            val content = window.contentManager.contents.firstOrNull { it.displayName == "Detail" } ?: return@activate

            window.contentManager.setSelectedContent(content)
        }
    }
}
