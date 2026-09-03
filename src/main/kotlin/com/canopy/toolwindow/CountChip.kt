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

class CountChip(private val entry: OutstandingCount) : JComponent() {

    private val boldFont = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
    private val labelFont = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

    init {
        isOpaque = false
        toolTipText = "${entry.count} file(s) ${entry.label}"
    }

    override fun getPreferredSize(): Dimension {
        val bold = getFontMetrics(boldFont)
        val width = JBUI.scale(PAD_X) * 2 + JBUI.scale(GAP) +
            bold.stringWidth(entry.count.toString()) +
            getFontMetrics(labelFont).stringWidth(entry.label)

        return Dimension(width, bold.height + JBUI.scale(PAD_Y) * 2)
    }

    /**
     * One size, whatever the layout around it is trying to do.
     *
     * Without a maximum a row of one chip stretches it across the card; without a minimum a
     * BoxLayout under any pressure at all squeezes it to nothing, since a bare JComponent reports
     * no minimum of its own.
     */
    override fun getMaximumSize(): Dimension = preferredSize

    override fun getMinimumSize(): Dimension = preferredSize

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val ink = toneColor(entry.tone)
            g.color = InsightUi.blendInto(InsightUi.raisedBackground(), ink, CHIP_TINT)
            g.fillRoundRect(0, 0, width, height, JBUI.scale(ARC), JBUI.scale(ARC))

            val bold = getFontMetrics(boldFont)
            val baseline = JBUI.scale(PAD_Y) + bold.ascent
            val count = entry.count.toString()

            g.font = boldFont
            g.color = ink
            g.drawString(count, JBUI.scale(PAD_X), baseline)

            g.font = labelFont
            g.color = UIUtil.getLabelDisabledForeground()
            g.drawString(entry.label, JBUI.scale(PAD_X + GAP) + bold.stringWidth(count), baseline)
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

private const val ARC = 9
private const val PAD_X = 8
private const val PAD_Y = 4
private const val GAP = 5
private const val CHIP_TINT = 0.22
