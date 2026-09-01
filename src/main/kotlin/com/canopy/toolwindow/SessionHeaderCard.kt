package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.SessionPresence
import com.canopy.model.sessionGlyph
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Which session is being reviewed, and whether anything it left behind needs doing.
 *
 * The same card the sidebar draws, given the room the review tab has: the state it is in, what it
 * is called, where and when it ran, and its outstanding work as counts rather than a sentence.
 */
class SessionHeaderCard : JPanel(BorderLayout()) {

    private val island = IslandPanel(BorderLayout(JBUI.scale(12), 0))
    private val glyph = JLabel()
    private val title = JLabel().apply { font = font.deriveFont(java.awt.Font.BOLD, font.size2D * TITLE_SCALE) }
    private val place = JLabel().apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelDisabledForeground()
    }
    private val chips = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(7)
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(InsightUi.GAP, InsightUi.GAP, InsightUi.GAP / 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(10, 12)
        island.islandColor = InsightUi.islandBackground()

        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(title.leftAligned())
            add(place.leftAligned())
            add(chips.leftAligned())
        }
        island.add(glyph, BorderLayout.WEST)
        island.add(text, BorderLayout.CENTER)
        add(island, BorderLayout.CENTER)
    }

    fun show(session: SessionDisplay?, attention: SessionAttention, presence: SessionPresence, counts: List<OutstandingCount>) {
        glyph.icon = GlyphIcon(
            { sessionGlyph(attention, presence, System.currentTimeMillis()) ?: "●" },
            glyphColor(attention),
            GLYPH_BOX
        )
        title.text = session?.displayName?.takeIf { it.isNotBlank() } ?: "No session selected"
        place.text = placeLine(session)
        toolTipText = session?.let(::tooltipFor)

        chips.removeAll()
        counts.forEach { chips.add(CountChip(it)) }
        chips.isVisible = counts.isNotEmpty()
        chips.revalidate()
        chips.repaint()
    }

    private fun glyphColor(attention: SessionAttention) = when (attention) {
        SessionAttention.NeedsPermission -> InsightUi.needsAttention()
        SessionAttention.WaitingForInput -> InsightUi.accent()
        SessionAttention.Working, SessionAttention.Compacting -> InsightUi.accent()
        SessionAttention.None -> UIUtil.getLabelDisabledForeground()
    }

    private fun <T : javax.swing.JComponent> T.leftAligned(): T = apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }
}

private const val TITLE_SCALE = 1.1f
private const val GLYPH_BOX = 22

/** Where it worked, how long ago, and how much was said, in that order of use. */
internal fun placeLine(session: SessionDisplay?, nowMillis: Long = System.currentTimeMillis()): String {
    if (session == null) return "Open a session to review what it changed"

    return listOfNotNull(
        placesFor(session),
        relativeAge(session.modified.toEpochMilli(), nowMillis),
        "${session.messageCount} messages"
    ).joinToString("   ")
}
