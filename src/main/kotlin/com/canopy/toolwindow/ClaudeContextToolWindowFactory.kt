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

class ClaudeContextToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")

        val parent = toolWindow.disposable
        addTab(toolWindow, "Detail", "canopy", detailPanel(project, parent))
        addTab(toolWindow, "Context", "context", ClaudeContextPanel(project, parent))
        addTab(toolWindow, "Commits", "tab-commits", com.canopy.insight.CommitsTabPanel(project, parent))
        addTab(toolWindow, "Plan", "tab-plan", PlanTabPanel(project, parent))
        addTab(toolWindow, "Messages", "tab-messages", MessagesTabPanel(project, parent))
        addTab(toolWindow, "Activity", "tab-activity", ActivityTabPanel(project, parent))

        toolWindow.setTitleActions(listOf(SelectTabAction(toolWindow)))
    }

    private fun detailPanel(project: Project, parent: com.intellij.openapi.Disposable): SessionDetailPanel {
        val sessions = com.canopy.services.ClaudeSessionService.getInstance(project)
        val panel = SessionDetailPanel(project, parent) { id -> sessions.getSessions().firstOrNull { it.sessionId == id } }

        project.messageBus.connect(parent).subscribe(
            com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : com.intellij.openapi.fileEditor.FileEditorManagerListener {
                override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                    val session = event.newFile as? com.canopy.editor.ClaudeSessionVirtualFile ?: return

                    panel.showSession(session.sessionId)
                }
            }
        )

        return panel
    }

    private fun addTab(toolWindow: ToolWindow, title: String, icon: String, panel: JComponent) {
        val content = ContentFactory.getInstance().createContent(panel, title, false)

        content.icon = IconLoader.getIcon("/icons/$icon.svg", ClaudeContextToolWindowFactory::class.java)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = project.basePath != null
}
