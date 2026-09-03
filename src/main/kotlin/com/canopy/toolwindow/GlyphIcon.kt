package com.canopy.toolwindow

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Resolved at paint time, not at construction: the main toolbar's file widget asks for an icon once
 * and keeps it, so a glyph baked in there freezes on whatever the state was when the tab opened.
 */
class GlyphIcon(
    private val glyphOf: () -> String,
    private val tintOf: () -> Color? = { null },
    boxSize: Int = BOX
) : Icon {

    constructor(glyph: String, tint: Color? = null, boxSize: Int = BOX) : this({ glyph }, { tint }, boxSize)

    private val size = JBUIScale.scale(boxSize)

    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        val glyph = glyphOf()
        component?.repaint(if (isSpinnerFrame(glyph)) SPINNER_FRAME_MS else IDLE_RECHECK_MS)
        if (glyph.isEmpty()) return

        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = tintOf() ?: component?.foreground ?: UIUtil.getLabelForeground()
            g.font = (component?.font ?: UIUtil.getLabelFont()).deriveFont(size * GLYPH_TO_BOX)
            val metrics = g.fontMetrics
            g.drawString(
                glyph,
                x + (size - metrics.stringWidth(glyph)) / 2f,
                y + (size - metrics.height) / 2f + metrics.ascent
            )
        } finally {
            g.dispose()
        }
    }
}

private const val BOX = 16
private const val IDLE_RECHECK_MS = 1_000L

private const val GLYPH_TO_BOX = 0.9f
