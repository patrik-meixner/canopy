package com.canopy.insight

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

/** One arc radius, one inset, one hairline: the tabs read as one surface rather than five widgets. */
object InsightUi {

    val ARC: Int get() = JBUI.scale(10)
    val GAP: Int get() = JBUI.scale(6)

    private const val SELECTED_TINT = 0.34
    private const val SELECTED_HOVER_TINT = 0.46
    private const val HOVER_TINT = 0.16

    private val tints = java.util.concurrent.ConcurrentHashMap<Double, Color>()

    private val ISLAND: Color = JBColor.lazy { JBUI.CurrentTheme.ToolWindow.background() }

    private val PANEL: Color = JBColor.lazy {
        com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme.defaultBackground
    }

    /** The card carries the accent; the strip behind it stays the list, so nothing frames the card. */
    fun cardBackground(selected: Boolean, hovered: Boolean = false): Color = when {
        selected && hovered -> accentTint(SELECTED_HOVER_TINT)
        selected -> accentTint(SELECTED_TINT)
        hovered -> accentTint(HOVER_TINT)
        else -> islandBackground()
    }

    fun hoverBackground(): Color = accentTint(HOVER_TINT)

    /**
     * The IDE's own accent, mixed into the list background rather than replacing it.
     *
     * One [JBColor] per strength, held: a lazy colour already re-reads the theme whenever it is
     * painted, so building a new one per row per repaint allocated for nothing.
     */
    fun accentTint(strength: Double): Color =
        tints.getOrPut(strength) { JBColor.lazy { blend(islandBackground(), accent(), strength) } }

    fun cardForeground(selected: Boolean): Color =
        if (selected) UIUtil.getLabelForeground() else UIUtil.getLabelForeground()

    fun mutedForeground(selected: Boolean): Color =
        if (selected) UIUtil.getLabelForeground() else JBColor.GRAY

    private fun blend(base: Color, accent: Color, strength: Double): Color = Color(
        (base.red + (accent.red - base.red) * strength).toInt().coerceIn(0, 255),
        (base.green + (accent.green - base.green) * strength).toInt().coerceIn(0, 255),
        (base.blue + (accent.blue - base.blue) * strength).toInt().coerceIn(0, 255)
    )

    /** The accent the IDE is themed with; there is no reason for the plugin to invent one. */
    fun accent(): Color = JBUI.CurrentTheme.Focus.focusColor()

    fun needsAttention(): Color = JBUI.CurrentTheme.Focus.errorColor(true)

    fun waiting(): Color = JBUI.CurrentTheme.Focus.warningColor(true)

    /** The tone the tool window's own toolbar has, so a card belongs to the panel it sits in. */
    fun islandBackground(): Color = ISLAND

    /** The strip behind the cards is the editor's ground, which is what the panel opens onto. */
    fun panelBackground(): Color = PANEL

}

/** A panel that paints itself as a rounded island instead of a square block. */
open class IslandPanel(layout: java.awt.LayoutManager) : JPanel(layout) {

    var islandColor: Color = InsightUi.islandBackground()

    init {
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = islandColor
            g.fillRoundRect(0, 0, width - 1, height - 1, InsightUi.ARC, InsightUi.ARC)
        } finally {
            g.dispose()
        }
        super.paintComponent(graphics)
    }
}
