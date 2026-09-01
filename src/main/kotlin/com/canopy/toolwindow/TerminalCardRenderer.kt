package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
import com.canopy.model.SessionDisplay
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/** A shell belonging to the session above it: the indent is what says which session that is. */
class TerminalCardRenderer(
    private val hoveredRow: () -> Int,
    private val isCloseHovered: () -> Boolean = { false }
) : TableCellRenderer {

    private val outer = object : JPanel(BorderLayout()) {
        override fun paintComponent(graphics: java.awt.Graphics) {
            graphics.color = InsightUi.panelBackground()
            graphics.fillRect(0, 0, width, height)
        }
    }
    private val island = IslandPanel(BorderLayout(JBUI.scale(6), 0))
    private val icon = JLabel(AllIcons.Debugger.Console)
    private val title = JLabel()
    private val stop = JLabel()

    init {
        outer.isOpaque = true
        outer.border = JBUI.Borders.empty(0, InsightUi.GAP + INDENT, 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(3, CARD_PADDING)
        stop.preferredSize = java.awt.Dimension(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)
        title.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

        island.add(icon, BorderLayout.WEST)
        island.add(title, BorderLayout.CENTER)
        island.add(stop, BorderLayout.EAST)
        outer.add(island, BorderLayout.CENTER)
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val terminal = value as? SessionDisplay ?: return outer

        val isHovered = row == hoveredRow()
        island.islandColor = InsightUi.cardBackground(selected, hovered = isHovered)
        stop.icon = when {
            !isHovered -> null
            isCloseHovered() -> AllIcons.Actions.CloseHovered
            else -> AllIcons.Actions.Close
        }
        title.text = terminal.displayName
        title.foreground = if (selected) InsightUi.cardForeground(true) else UIUtil.getLabelDisabledForeground()

        return outer
    }

    companion object {
        val INDENT = JBUI.scale(16)
        val ROW_HEIGHT = JBUI.scale(26)
    }
}
