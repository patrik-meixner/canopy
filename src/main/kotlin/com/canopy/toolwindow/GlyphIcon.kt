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
 * nothing.
 *
 * The glyph is resolved at paint time rather than when the icon is built: the editor tab strip asks
 * for a file's icon constantly, but the main toolbar's file widget asks once and keeps what it got,
 * so an icon that carried a fixed glyph froze there on whichever state happened to be current when
 * the tab was selected.
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
        // Asking for the next repaint from the paint itself keeps the cost to the glyphs actually
        // on screen: a spinner at its own frame rate, anything else only often enough to notice a
        // state it cannot be told about.
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

/** Matches the weight the same glyph has in the session list, so the two read as one thing. */
private const val GLYPH_TO_BOX = 0.9f
