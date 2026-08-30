package com.canopy.insight

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants

/**
 * A vertical stack of real card components rather than a list of rendered cells.
 *
 * A JList measures one renderer instance and caches the size, so a card that wraps its text to the
 * list width fights that cache: the row overflows, and it changes height whenever anything is
 * repainted. Real components wrap to whatever width the viewport gives them, once.
 */
class CardListPanel(emptyText: String) : JPanel(BorderLayout()) {

    private val stack = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getListBackground()
        border = JBUI.Borders.empty(InsightUi.GAP / 2, 0, InsightUi.GAP, 0)
    }

    private val empty = JBLabel(emptyText, SwingConstants.CENTER).apply {
        foreground = UIUtil.getLabelDisabledForeground()
        isOpaque = false
    }

    private val scroll: JScrollPane = ScrollPaneFactory.createScrollPane(stack, true).apply {
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        viewport.background = UIUtil.getListBackground()
        background = UIUtil.getListBackground()
    }

    init {
        background = UIUtil.getListBackground()
        add(scroll, BorderLayout.CENTER)
    }

    fun setCards(cards: List<JComponent>) {
        stack.removeAll()
        if (cards.isEmpty()) {
            stack.add(Box.createVerticalStrut(JBUI.scale(40)))
            stack.add(empty.alignedToWidth())
        } else {
            cards.forEach { stack.add(it.alignedToWidth()) }
        }
        stack.add(Box.createVerticalGlue())
        stack.revalidate()
        stack.repaint()
    }

    fun scrollToTop() {
        scroll.verticalScrollBar.value = 0
    }

    /** BoxLayout hands a component its preferred width unless the maximum says otherwise. */
    private fun <T : JComponent> T.alignedToWidth(): T = apply {
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, maximumSize.height)
    }
}
