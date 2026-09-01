package com.canopy.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content

/**
 * Every tab of this tool window, including the ones the header had no room for.
 *
 * A narrow sidebar simply drops the tabs that do not fit, and a tab nobody can reach is a tab that
 * does not exist. This lists all of them, so how wide the window happens to be stops deciding what
 * the plugin can do.
 */
class SelectTabAction(private val toolWindow: ToolWindow) : AnAction(
    "Show All Tabs",
    "Pick a tab, including any the header has no room for",
    AllIcons.General.ChevronDown
), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val manager = toolWindow.contentManager
        val contents = manager.contents.toList()
        val over = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return

        val step = object : BaseListPopupStep<Content>(null, contents) {
            override fun getTextFor(value: Content): String = value.displayName.orEmpty()

            override fun getIconFor(value: Content) = value.icon

            override fun onChosen(value: Content, finalChoice: Boolean): PopupStep<*>? {
                manager.setSelectedContent(value, true)

                return FINAL_CHOICE
            }
        }
        step.defaultOptionIndex = contents.indexOf(manager.selectedContent).coerceAtLeast(0)

        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(over)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = toolWindow.contentManager.contentCount > 1
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
