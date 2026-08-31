package com.canopy.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * The window holding everything about the session on screen.
 *
 * Opening a session from the list is the start of reviewing it, so the review has to come forward
 * on its own: it no longer lives beside the list it was opened from.
 */
object SessionWindow {

    const val ID = "Session"

    fun activateDetail(project: Project) {
        val window = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return

        // A window the user has closed stays closed: switching sessions is not a reason to reopen it.
        if (!window.isVisible) return

        window.activate {
            val content = window.contentManager.contents.firstOrNull { it.displayName == "Detail" } ?: return@activate

            window.contentManager.setSelectedContent(content)
        }
    }
}
