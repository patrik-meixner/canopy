package com.canopy.insight

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

class TurnCard(private val turn: ActivityTurn, private val onOpenFile: (String) -> Unit) : JPanel(BorderLayout()) {

    private val island = IslandPanel(BorderLayout(JBUI.scale(10), 0))

    init {
        isOpaque = false
        border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP, 0, InsightUi.GAP)
        island.border = JBUI.Borders.empty(9, 11)

        island.add(ordinalChip(), BorderLayout.WEST)
        island.add(body(), BorderLayout.CENTER)
        add(island, BorderLayout.CENTER)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) = highlight(true)

            override fun mouseExited(event: MouseEvent) = highlight(false)
        })
    }

    private fun highlight(hovered: Boolean) {
        island.islandColor = if (hovered) InsightUi.hoverBackground() else InsightUi.islandBackground()
        island.repaint()
    }

    private fun ordinalChip() = JLabel(turn.ordinal.takeIf { it > 0 }?.toString().orEmpty(), SwingConstants.CENTER).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelDisabledForeground()
        verticalAlignment = SwingConstants.TOP
        preferredSize = Dimension(JBUI.scale(22), 0)
    }

    private fun body() = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(prompt())
        add(summary())
        turn.filesWritten.take(NAMED_FILES).forEach { add(fileRow(it)) }
        remainingFiles()?.let { add(it) }
    }

    private fun prompt() = JTextArea(turn.prompt.ifBlank { "Before your first message" }).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        isFocusable = false
        border = JBUI.Borders.empty()
        font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
        foreground = UIUtil.getLabelForeground()
        alignmentX = LEFT_ALIGNMENT
    }

    private fun summary() = JLabel(listOfNotNull(clockTime(turn.atMillis), turnSummary(turn)).joinToString("   ")).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelDisabledForeground()
        border = JBUI.Borders.emptyTop(4)
        alignmentX = LEFT_ALIGNMENT
    }

    private fun fileRow(path: String) = JLabel(shortPath(path), com.intellij.icons.AllIcons.Actions.Edit, SwingConstants.LEFT).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = InsightUi.accent()
        toolTipText = path
        border = JBUI.Borders.emptyTop(3)
        alignmentX = LEFT_ALIGNMENT
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) = onOpenFile(path)
        })
    }

    private fun remainingFiles(): JLabel? {
        val rest = turn.filesWritten.size - NAMED_FILES
        if (rest <= 0) return null

        return JLabel("and $rest more").apply {
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            foreground = UIUtil.getLabelDisabledForeground()
            border = JBUI.Borders.emptyTop(3)
            alignmentX = LEFT_ALIGNMENT
        }
    }
}

private const val NAMED_FILES = 6
