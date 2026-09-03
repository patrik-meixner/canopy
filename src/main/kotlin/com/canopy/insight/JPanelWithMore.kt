package com.canopy.insight

import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class JPanelWithMore(list: JComponent, more: MoreRow) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        add(list, BorderLayout.CENTER)
        add(more, BorderLayout.SOUTH)
    }
}
