package com.canopy.insight

import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Components in a row, with space between them and none around them.
 *
 * FlowLayout inserts its horizontal gap before the first component and after the last as well as
 * between them, so a row laid out with it never lines up with anything above it.
 */
fun rowOf(gap: Int, items: List<JComponent>): JPanel = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    alignmentX = Component.LEFT_ALIGNMENT

    items.forEachIndexed { index, item ->
        if (index > 0) add(Box.createHorizontalStrut(JBUI.scale(gap)))
        add(item)
    }
    add(Box.createHorizontalGlue())
}
