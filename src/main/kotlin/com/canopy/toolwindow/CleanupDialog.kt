package com.canopy.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Names every worktree that would go, and why, before any of them does.
 *
 * Removing a worktree removes its directory; a list of names with a count would not be enough to
 * decide from.
 */
class CleanupDialog(project: Project, private val candidates: List<CleanupCandidate>) : DialogWrapper(project) {

    private val list = CheckBoxList<CleanupCandidate>()

    init {
        title = "Delete Merged Worktrees"
        setOKButtonText("Delete Selected")
        candidates.forEach { list.addItem(it, "${it.name}    ${it.branch ?: "detached"}    ${it.reason}", true) }
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
        preferredSize = Dimension(JBUI.scale(620), JBUI.scale(320))
        border = JBUI.Borders.empty(8)
        add(JBLabel("Each of these is merged, has nothing uncommitted, and no session is using it.").apply {
            foreground = UIUtil.getLabelDisabledForeground()
        }, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER)
    }

    fun selected(): List<CleanupCandidate> = candidates.filter { list.isItemSelected(it) }
}
