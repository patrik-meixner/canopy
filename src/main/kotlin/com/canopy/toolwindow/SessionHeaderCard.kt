package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
import com.canopy.model.SessionAttention
import com.canopy.model.SessionDisplay
import com.canopy.model.SessionState
import com.canopy.model.sessionGlyph
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

class SessionHeaderCard : JPanel(BorderLayout()) {

    private val island = IslandPanel(BorderLayout(JBUI.scale(12), 0))
    private val glyph = JLabel()
    private val title = JLabel().apply { font = font.deriveFont(java.awt.Font.BOLD, font.size2D * TITLE_SCALE) }
    private val place = JLabel().apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelDisabledForeground()
    }
    private val chips = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(8)
    }
    private val evidence = JLabel(ESTIMATED_NOTE).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = UIUtil.getLabelDisabledForeground()
        toolTipText = ESTIMATED_TOOLTIP
        isVisible = false
    }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(InsightUi.GAP, InsightUi.GAP, InsightUi.GAP / 2, InsightUi.GAP)
        island.border = JBUI.Borders.empty(12, 14)
        island.islandColor = InsightUi.raisedBackground()
        island.outlineColor = InsightUi.cardBorder()

        glyph.verticalAlignment = JLabel.TOP
        glyph.border = JBUI.Borders.emptyTop(2)
        glyph.preferredSize = Dimension(JBUI.scale(GLYPH_BOX), JBUI.scale(GLYPH_BOX))

        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(title.leftAligned())
            add(place.leftAligned())
            add(evidence.leftAligned())
            add(chips.leftAligned())
        }
        island.add(glyph, BorderLayout.WEST)
        island.add(text, BorderLayout.CENTER)
        add(island, BorderLayout.CENTER)
    }

    fun show(session: SessionDisplay?, counts: List<OutstandingCount>, isEstimated: Boolean, stateOf: () -> SessionState) {
        glyph.icon = GlyphIcon(
            { stateOf().let { sessionGlyph(it.attention, it.presence, System.currentTimeMillis()) } ?: "●" },
            { glyphColor(stateOf().attention) },
            GLYPH_BOX
        )
        title.text = session?.displayName?.takeIf { it.isNotBlank() } ?: "No session selected"
        place.text = placeLine(session)
        evidence.isVisible = isEstimated && session != null
        toolTipText = session?.let(::tooltipFor)

        chips.removeAll()
        chips.add(com.canopy.insight.rowOf(CHIP_GAP, counts.map(::CountChip)), BorderLayout.CENTER)
        chips.isVisible = counts.isNotEmpty()
        revalidate()
        repaint()
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

private const val TITLE_SCALE = 1.15f
private const val GLYPH_BOX = 20
private const val CHIP_GAP = 6

internal fun placeLine(session: SessionDisplay?, nowMillis: Long = System.currentTimeMillis()): String {
    if (session == null) return "Open a session to review what it changed"

    return listOfNotNull(
        placesFor(session),
        relativeAge(session.modified.toEpochMilli(), nowMillis),
        "${session.messageCount} messages"
    ).joinToString("   ")
}

private const val ESTIMATED_NOTE = "Estimated from the transcript"
private const val ESTIMATED_TOOLTIP = "This session was started outside Canopy, or before its hooks kept a ledger, " +
    "so which files are its own is read from the tools it called and the dates on the files. " +
    "Sessions started here from now on record every change exactly."
