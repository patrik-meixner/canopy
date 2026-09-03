package com.canopy.insight

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel

object InsightUi {

    val ARC: Int get() = JBUI.scale(10)
    val GAP: Int get() = JBUI.scale(6)

    private const val SELECTED_TINT = 0.34
    private const val SELECTED_HOVER_TINT = 0.46
    private const val HOVER_TINT = 0.16
    private const val RAISED_TINT = 0.10
    private const val RUNNING_TINT = 0.09
    private const val BORDER_TINT = 0.30

    private val tints = java.util.concurrent.ConcurrentHashMap<Double, Color>()

    private val ISLAND: Color = JBColor.lazy { JBUI.CurrentTheme.ToolWindow.background() }

    private val PANEL: Color = JBColor.lazy {
        com.intellij.openapi.editor.colors.EditorColorsManager.getInstance().globalScheme.defaultBackground
    }

    fun cardBackground(selected: Boolean, hovered: Boolean = false, running: Boolean = false): Color = when {
        selected && hovered -> accentTint(SELECTED_HOVER_TINT)
        selected -> accentTint(SELECTED_TINT)
        hovered -> accentTint(HOVER_TINT)
        running -> accentTint(RUNNING_TINT)
        else -> islandBackground()
    }

    fun hoverBackground(): Color = accentTint(HOVER_TINT)

    fun accentTint(strength: Double): Color =
        tints.getOrPut(strength) { JBColor.lazy { blend(islandBackground(), accent(), strength) } }

    fun cardForeground(selected: Boolean): Color =
        if (selected) UIUtil.getLabelForeground() else UIUtil.getLabelForeground()

    fun mutedForeground(selected: Boolean): Color =
        if (selected) UIUtil.getLabelForeground() else JBColor.GRAY

    fun blendInto(base: Color, ink: Color, strength: Double): Color = blend(base, ink, strength)

    private fun blend(base: Color, accent: Color, strength: Double): Color = Color(
        (base.red + (accent.red - base.red) * strength).toInt().coerceIn(0, 255),
        (base.green + (accent.green - base.green) * strength).toInt().coerceIn(0, 255),
        (base.blue + (accent.blue - base.blue) * strength).toInt().coerceIn(0, 255)
    )

    fun accent(): Color = JBUI.CurrentTheme.Focus.focusColor()

    fun needsAttention(): Color = JBUI.CurrentTheme.Focus.errorColor(true)

    fun waiting(): Color = JBUI.CurrentTheme.Focus.warningColor(true)

    fun islandBackground(): Color = ISLAND

    fun panelBackground(): Color = PANEL

    fun emptyBackground(): Color = ISLAND

    fun raisedBackground(): Color = accentTint(RAISED_TINT)

    fun cardBorder(): Color = accentTint(BORDER_TINT)

}

open class IslandPanel(layout: java.awt.LayoutManager) : JPanel(layout) {

    var islandColor: Color = InsightUi.islandBackground()

    init {
        isOpaque = false
    }

    var outlineColor: Color? = null

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = islandColor
            g.fillRoundRect(0, 0, width - 1, height - 1, InsightUi.ARC, InsightUi.ARC)
            outlineColor?.let {
                g.color = it
                g.drawRoundRect(0, 0, width - 1, height - 1, InsightUi.ARC, InsightUi.ARC)
            }
        } finally {
            g.dispose()
        }
        super.paintComponent(graphics)
    }
}
