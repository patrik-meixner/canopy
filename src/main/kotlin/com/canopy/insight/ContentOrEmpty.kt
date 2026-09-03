package com.canopy.insight

import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ContentOrEmpty(content: JComponent, empty: EmptyState) : JPanel(CardLayout()) {

    init {
        isOpaque = false
        add(content, CONTENT)
        add(empty, EMPTY)
    }

    fun showEmpty(isEmpty: Boolean) {
        (layout as CardLayout).show(this, if (isEmpty) EMPTY else CONTENT)
    }
}

private const val CONTENT = "content"
private const val EMPTY = "empty"
