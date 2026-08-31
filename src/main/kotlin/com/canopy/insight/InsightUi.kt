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

    /** The card carries the accent; the strip behind it stays the list, so nothing frames the card. */
    fun cardBackground(selected: Boolean, hovered: Boolean = false): Color = when {
        selected && hovered -> accentTint(SELECTED_HOVER_TINT)
        selected -> accentTint(SELECTED_TINT)
        hovered -> accentTint(HOVER_TINT)
        else -> islandBackground()
    }

    fun hoverBackground(): Color = accentTint(HOVER_TINT)

    /** The IDE's own accent, mixed into the list background rather than replacing it. */
    fun accentTint(strength: Double): Color = JBColor.lazy { blend(UIUtil.getListBackground(), accent(), strength) }

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

    /** A touch off the list background, so a card reads as a raised island without a border. */
    fun islandBackground(): Color = JBColor.lazy {
        val base = UIUtil.getListBackground()
        if (JBColor.isBright()) shift(base, -6) else shift(base, 10)
    }

    private fun shift(color: Color, delta: Int): Color = Color(
        (color.red + delta).coerceIn(0, 255),
        (color.green + delta).coerceIn(0, 255),
        (color.blue + delta).coerceIn(0, 255)
    )
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
