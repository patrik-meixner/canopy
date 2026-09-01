package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * A number and what it counts, on a ground tinted by what it means.
 *
 * The number carries the weight and the label stays quiet, so a row of these reads as three
 * quantities rather than as a sentence to parse.
 */
class CountChip(private val entry: OutstandingCount) : JComponent() {

    private val boldFont = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
    private val labelFont = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

    init {
        isOpaque = false
        toolTipText = "${entry.count} file(s) ${entry.label}"
    }

    override fun getPreferredSize(): Dimension {
        val metrics = getFontMetrics(boldFont)
        val labelMetrics = getFontMetrics(labelFont)
        val width = metrics.stringWidth(entry.count.toString()) + labelMetrics.stringWidth(entry.label) + JBUI.scale(PADDING * 2 + GAP)

        return Dimension(width, metrics.height + JBUI.scale(PADDING))
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val ink = toneColor(entry.tone)
            g.color = InsightUi.blendInto(InsightUi.islandBackground(), ink, CHIP_TINT)
            g.fillRoundRect(0, 0, width, height, JBUI.scale(ARC), JBUI.scale(ARC))

            val baseline = (height + getFontMetrics(boldFont).ascent - getFontMetrics(boldFont).descent) / 2
            g.font = boldFont
            g.color = ink
            val count = entry.count.toString()
            g.drawString(count, JBUI.scale(PADDING), baseline)

            g.font = labelFont
            g.color = UIUtil.getLabelDisabledForeground()
            g.drawString(entry.label, JBUI.scale(PADDING + GAP) + getFontMetrics(boldFont).stringWidth(count), baseline)
        } finally {
            g.dispose()
        }
    }

    private fun toneColor(tone: OutstandingTone): Color = when (tone) {
        OutstandingTone.Yours -> InsightUi.accent()
        OutstandingTone.Ahead -> InsightUi.waiting()
        OutstandingTone.Untracked -> JBColor.GRAY
        OutstandingTone.Clear -> JBColor.GRAY
    }
}

private const val ARC = 8
private const val PADDING = 8
private const val GAP = 5
private const val CHIP_TINT = 0.22
