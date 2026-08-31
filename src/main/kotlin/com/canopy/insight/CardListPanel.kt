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
import javax.swing.SwingUtilities
import javax.swing.Timer
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

    private var shown = 0
    private var glide: Timer? = null

    init {
        background = UIUtil.getListBackground()
        add(scroll, BorderLayout.CENTER)
    }

    fun setCards(cards: List<JComponent>) {
        val bar = scroll.verticalScrollBar
        val keptValue = bar.value
        val wasAtBottom = bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(BOTTOM_SLACK)
        val grew = cards.size > shown

        shown = cards.size
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

        // The new height only exists after layout, so the decision runs once the stack has one.
        SwingUtilities.invokeLater {
            if (grew && wasAtBottom) glideTo(bar.maximum - bar.visibleAmount) else bar.value = keptValue
        }
    }

    fun scrollToTop() {
        scroll.verticalScrollBar.value = 0
    }

    /** A jump loses where you were reading; a short glide keeps the new message tied to it. */
    private fun glideTo(target: Int) {
        val bar = scroll.verticalScrollBar
        val from = bar.value
        val distance = target - from
        if (distance <= 0) return

        glide?.stop()
        val startedAt = System.currentTimeMillis()
        glide = Timer(GLIDE_STEP_MS) { event ->
            val progress = ((System.currentTimeMillis() - startedAt).toDouble() / GLIDE_MS).coerceIn(0.0, 1.0)
            bar.value = from + (distance * eased(progress)).toInt()
            if (progress >= 1.0) (event.source as Timer).stop()
        }.also { it.start() }
    }

    /** BoxLayout hands a component its preferred width unless the maximum says otherwise. */
    private fun <T : JComponent> T.alignedToWidth(): T = apply {
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, maximumSize.height)
    }
}

private const val GLIDE_MS = 220.0
private const val GLIDE_STEP_MS = 12
private const val BOTTOM_SLACK = 80

/** Ease-out: quick off the mark, settling rather than stopping dead. */
internal fun eased(progress: Double): Double = 1 - (1 - progress) * (1 - progress)
