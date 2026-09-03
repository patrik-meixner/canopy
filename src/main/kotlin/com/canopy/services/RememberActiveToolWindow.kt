package com.canopy.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

const val CANOPY_TOOL_WINDOW = "Canopy"

private val LOG = Logger.getInstance(RememberActiveToolWindow::class.java)

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
        if (CanopyShutdown.isClosing()) return
        val canopy = toolWindowManager.getToolWindow(CANOPY_TOOL_WINDOW) ?: return
        val settings = com.canopy.settings.CanopySettings.getInstance().state
        val restore = SidebarRestore.getInstance(project)

        when (sidebarAction(restore.isSettling, settings.sidebarWasOpen, canopy.isVisible)) {
            SidebarAction.Ignore -> return
            SidebarAction.Reopen -> {
                if (!restore.takeAttempt()) return

                LOG.info("Canopy: the sidebar was closed while ${project.name} was still opening — putting it back")
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) canopy.activate(null, false)
                }
            }
            SidebarAction.Record -> {
                settings.sidebarWasOpen = canopy.isVisible
                LOG.info("Canopy: sidebar ${if (canopy.isVisible) "open" else "closed"} — remembered for the next start")
            }
        }
    }
}
