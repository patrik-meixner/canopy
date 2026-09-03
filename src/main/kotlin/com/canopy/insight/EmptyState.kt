package com.canopy.insight

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.SwingConstants
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class EmptyState(icon: Icon, title: String, hint: String) : JPanel(GridBagLayout()) {

    init {
        background = InsightUi.emptyBackground()

        val column = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            isOpaque = false
            add(JLabel(illustrationOf(icon), SwingConstants.CENTER), BorderLayout.NORTH)
            add(JBLabel(title, SwingConstants.CENTER).apply {
                font = font.deriveFont(java.awt.Font.BOLD, font.size2D * TITLE_SCALE)
                foreground = UIUtil.getLabelForeground()
            }, BorderLayout.CENTER)
            add(wrapping(hint), BorderLayout.SOUTH)
        }

        add(column, GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = JBUI.insets(0, HINT_MARGIN)
        })
    }

    /** A text pane rather than a label: only a styled document both wraps and centres its lines. */
    private fun wrapping(hint: String) = JTextPane().apply {
        isEditable = false
        isOpaque = false
        isFocusable = false
        border = JBUI.Borders.emptyTop(2)
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getLabelDisabledForeground()
        text = hint

        val centred = SimpleAttributeSet()
        StyleConstants.setAlignment(centred, StyleConstants.ALIGN_CENTER)
        styledDocument.setParagraphAttributes(0, styledDocument.length, centred, false)
    }
}

private const val TITLE_SCALE = 1.15f
private const val HINT_MARGIN = 28
