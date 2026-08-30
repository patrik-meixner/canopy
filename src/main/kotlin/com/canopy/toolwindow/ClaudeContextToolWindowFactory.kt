package com.canopy.toolwindow

import com.canopy.insight.ActivityTabPanel
import com.canopy.insight.MessagesTabPanel
import com.canopy.insight.PlanTabPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

/**
 * Everything about the session on screen that is not its terminal: what it was asked, what it is
 * doing, what it plans to do, where it wrote, and what it reads as context.
 */
class ClaudeContextToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")

        val parent = toolWindow.disposable
        addTab(toolWindow, "Context", "context", ClaudeContextPanel(project, parent))
        addTab(toolWindow, "Plan", "tab-plan", PlanTabPanel(project, parent))
        addTab(toolWindow, "Messages", "tab-messages", MessagesTabPanel(project, parent))
        addTab(toolWindow, "Activity", "tab-activity", ActivityTabPanel(project, parent))
    }

    private fun addTab(toolWindow: ToolWindow, title: String, icon: String, panel: JComponent) {
        val content = ContentFactory.getInstance().createContent(panel, title, false)

        content.icon = IconLoader.getIcon("/icons/$icon.svg", ClaudeContextToolWindowFactory::class.java)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = project.basePath != null
}
