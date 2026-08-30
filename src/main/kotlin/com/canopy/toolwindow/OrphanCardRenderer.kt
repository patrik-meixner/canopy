package com.canopy.toolwindow

import com.canopy.editor.ClaudeSessionIconProvider
import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
import com.canopy.model.OrphanReason

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * A worktree with no session, as an island: what it is called and why it is still here.
 *
 * The reason is a sentence, not a word, and on one line it pushed the name and its repository out
 * of view exactly when they were needed to decide whether to delete it.
 */
class OrphanCardRenderer : ListCellRenderer<OrphanWorktree> {

    private val outer = JPanel(BorderLayout())
    private val island = IslandPanel(BorderLayout(JBUI.scale(8), 0))
    private val icon = JLabel(ClaudeSessionIconProvider.TREE_ICON)
    private val title = JLabel()
    private val reason = JLabel()

    init {
        outer.isOpaque = true
        outer.border = JBUI.Borders.empty(2, InsightUi.GAP, 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(6, 10)
        title.font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
        reason.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        icon.verticalAlignment = JLabel.TOP

        island.add(icon, BorderLayout.WEST)
        island.add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(title)
            add(reason)
        }, BorderLayout.CENTER)
        outer.add(island, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out OrphanWorktree>,
        value: OrphanWorktree?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ): Component {
        if (value == null) return outer

        outer.background = list.background
        island.islandColor = InsightUi.cardBackground(selected)
        title.text = value.name
        title.foreground = InsightUi.cardForeground(selected)
        reason.text = describeReason(value)
        reason.foreground = if (selected) InsightUi.cardForeground(true) else UIUtil.getLabelDisabledForeground()
        outer.toolTipText = value.path

        return outer
    }
}

internal fun describeReason(orphan: OrphanWorktree): String {
    val cause = when (val reason = orphan.reason) {
        is OrphanReason.NoSession -> orphan.branch ?: "detached HEAD"
        is OrphanReason.EmptyDirectory -> "empty directory, safe to delete"
        is OrphanReason.LeftoverFiles -> "left behind after removal: ${reason.names.joinToString(", ")}"
        is OrphanReason.MissingGitDir -> "git metadata gone (${reason.gitDir})"
        is OrphanReason.OwnedByOtherRepo -> "worktree of another repo"
    }

    return listOf(orphan.repoLabel, cause).filter { it.isNotEmpty() }.joinToString("   ")
}
