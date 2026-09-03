package com.canopy.insight

import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JPanel

class MoreRow(private val onMore: () -> Unit) : JPanel(FlowLayout(FlowLayout.CENTER, 0, JBUI.scale(8))) {

    private val link = ActionLink("") { onMore() }

    init {
        isOpaque = false
        add(link)
    }

    fun show(remaining: Int, step: Int) {
        isVisible = remaining > 0
        link.text = "Show ${minOf(remaining, step)} more of $remaining"
    }
}
