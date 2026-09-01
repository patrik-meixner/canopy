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
 * One glyph of the session vocabulary, centred in a fixed box.
 *
 * A fixed box is what keeps a tab and a row from resizing as the state under them changes, and it
 * is why this is an icon rather than a label's text. An empty glyph reserves the box and draws
 * nothing; a spinner frame asks for the next repaint from the paint itself, so only the glyphs
 * actually on screen cost anything.
 */
class GlyphIcon(private val glyph: String, private val tint: Color? = null, boxSize: Int = BOX) : Icon {

    private val size = JBUIScale.scale(boxSize)
    private val spins = isSpinnerFrame(glyph)

    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        if (glyph.isEmpty()) return

        val drawn = if (spins) spinnerFrame(System.currentTimeMillis()) else glyph
        if (spins) component?.repaint(SPINNER_FRAME_MS)

        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = tint ?: component?.foreground ?: UIUtil.getLabelForeground()
            g.font = (component?.font ?: UIUtil.getLabelFont()).deriveFont(size * GLYPH_TO_BOX)
            val metrics = g.fontMetrics
            g.drawString(
                drawn,
                x + (size - metrics.stringWidth(drawn)) / 2f,
                y + (size - metrics.height) / 2f + metrics.ascent
            )
        } finally {
            g.dispose()
        }
    }
}

private const val BOX = 16

/** Matches the weight the same glyph has in the session list, so the two read as one thing. */
private const val GLYPH_TO_BOX = 0.9f
