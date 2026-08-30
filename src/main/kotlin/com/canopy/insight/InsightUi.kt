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

    fun cardBackground(selected: Boolean): Color =
        if (selected) UIUtil.getListSelectionBackground(true) else islandBackground()

    fun cardForeground(selected: Boolean): Color =
        if (selected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()

    fun mutedForeground(selected: Boolean): Color =
        if (selected) UIUtil.getListSelectionForeground(true) else JBColor.GRAY

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
