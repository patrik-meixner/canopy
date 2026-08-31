package com.canopy.insight

import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** A scrolling list with the row that reveals the rest of it pinned below. */
class JPanelWithMore(list: JComponent, more: MoreRow) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        add(list, BorderLayout.CENTER)
        add(more, BorderLayout.SOUTH)
    }
}
