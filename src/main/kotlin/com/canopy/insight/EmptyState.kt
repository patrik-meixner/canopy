package com.canopy.insight

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * What a tab says when it has nothing to show.
 *
 * A tab with no content used to be an unbroken slab of colour, which reads as a tab that failed to
 * load rather than one with nothing in it yet. Every tab says the same three things in the same
 * place: what is missing, and what would put something there.
 */
class EmptyState(icon: Icon, title: String, hint: String) : JPanel(GridBagLayout()) {

    init {
        background = InsightUi.emptyBackground()

        val column = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        column.add(centred(JLabel(illustrationOf(icon))))
        column.add(javax.swing.Box.createVerticalStrut(JBUI.scale(12)))
        column.add(centred(JBLabel(title, SwingConstants.CENTER).apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size2D * TITLE_SCALE)
            foreground = UIUtil.getLabelForeground()
        }))
        column.add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)))
        column.add(centred(JBLabel(hint, SwingConstants.CENTER).apply {
            foreground = UIUtil.getLabelDisabledForeground()
            maximumSize = Dimension(JBUI.scale(HINT_WIDTH), Short.MAX_VALUE.toInt())
        }))

        add(column)
    }

    private fun <T : javax.swing.JComponent> centred(component: T): T = component.apply {
        alignmentX = Component.CENTER_ALIGNMENT
        isOpaque = false
    }
}

private const val TITLE_SCALE = 1.15f
private const val HINT_WIDTH = 280
