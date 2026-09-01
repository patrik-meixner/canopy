package com.canopy.insight

import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * A vertical stack of real card components rather than a list of rendered cells.
 *
 * A JList measures one renderer instance and caches the size, so a card that wraps its text to the
 * list width fights that cache: the row overflows, and it changes height whenever anything is
 * repainted. Real components wrap to whatever width the viewport gives them, once.
 */
class CardListPanel(private val emptyState: EmptyState) : JPanel(CardLayout()) {

    private val stack = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = InsightUi.panelBackground()
        border = JBUI.Borders.empty(InsightUi.GAP / 2, 0, InsightUi.GAP, 0)
    }

    private val scroll: JScrollPane = ScrollPaneFactory.createScrollPane(stack, true).apply {
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        viewport.background = InsightUi.panelBackground()
        background = InsightUi.panelBackground()
    }

    private var shown = 0
    private var keys: List<String>? = null
    private var glide: Timer? = null
    private var more: MoreRow? = null
    private var moreAtTop = false
    private var followingNewest = false

    init {
        background = InsightUi.panelBackground()
        add(scroll, CARDS)
        add(emptyState, EMPTY)

        // The height the stack will have does not exist until it has been laid out, and images in
        // the cards change it again after that. Following the bottom until the reader scrolls is
        // what makes "open on the newest message" survive both.
        scroll.verticalScrollBar.model.addChangeListener { if (followingNewest) jumpToNewest() }
        scroll.addMouseWheelListener { followingNewest = false }
        scroll.verticalScrollBar.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                followingNewest = false
            }
        })
    }

    /**
     * The row that reveals the rest of the list, at whichever end the rest actually lies: older
     * messages are above the newest, older sessions below them.
     */
    fun onMore(remaining: Int, step: Int, atTop: Boolean = false, reveal: () -> Unit) {
        val row = more ?: MoreRow(reveal).also { more = it }

        moreAtTop = atTop
        row.show(remaining, step)
    }

    fun setCards(cards: List<JComponent>) = setCards(cards, cards.indices.map(Int::toString))

    /**
     * Rebuilding an unchanged list is what made the view jump: the stack is torn down and laid out
     * again on every tick, and a scroll position restored as a raw pixel offset lands somewhere else
     * once the new heights settle. Identical keys mean identical cards, so nothing is touched.
     */
    fun setCards(cards: List<JComponent>, cardKeys: List<String>) {
        if (cardKeys == keys && cards.size == shown) return

        val first = keys == null
        keys = cardKeys
        showCard(if (cards.isEmpty()) EMPTY else CARDS)
        if (cards.isEmpty()) {
            shown = 0
            stack.removeAll()
            return
        }

        val bar = scroll.verticalScrollBar
        val keptValue = bar.value
        val wasAtBottom = bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(BOTTOM_SLACK)
        val grew = cards.size > shown

        shown = cards.size
        stack.removeAll()
        if (moreAtTop) more?.let { stack.add(it.alignedToWidth()) }
        cards.forEach { stack.add(it.alignedToWidth()) }
        if (!moreAtTop) more?.let { stack.add(it.alignedToWidth()) }
        stack.add(Box.createVerticalGlue())
        stack.revalidate()
        stack.repaint()

        if (first) return followNewest()

        SwingUtilities.invokeLater {
            if (grew && wasAtBottom) glideTo(bar.maximum - bar.visibleAmount) else bar.value = keptValue
        }
    }

    /** Stay at the newest entry through every relayout, until the reader scrolls away from it. */
    fun followNewest() {
        followingNewest = true
        jumpToNewest()
    }

    fun scrollToTop() {
        followingNewest = false
        scroll.verticalScrollBar.value = 0
    }

    private fun jumpToNewest() {
        val bar = scroll.verticalScrollBar
        val bottom = bar.maximum - bar.visibleAmount
        if (bar.value != bottom) bar.value = bottom
    }

    private fun showCard(name: String) {
        (layout as CardLayout).show(this, name)
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

    /**
     * Full width, but never taller than the card needs.
     *
     * A component's default maximum height is effectively unbounded, and BoxLayout shares whatever
     * space is left among everything that will take it - so a handful of cards spread themselves
     * down the panel with a gap between each.
     */
    private fun <T : JComponent> T.alignedToWidth(): T = apply {
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}

private const val CARDS = "cards"
private const val EMPTY = "empty"
private const val GLIDE_MS = 220.0
private const val GLIDE_STEP_MS = 12
private const val BOTTOM_SLACK = 80

/** Ease-out: quick off the mark, settling rather than stopping dead. */
internal fun eased(progress: Double): Double = 1 - (1 - progress) * (1 - progress)
