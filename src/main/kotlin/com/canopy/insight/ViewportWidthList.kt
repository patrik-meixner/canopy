package com.canopy.insight

import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel

class ViewportWidthList<T>(model: DefaultListModel<T>) : JBList<T>(model) {

    init {
        // Rows are deliberately narrower than their content here, which is exactly the case the
        // platform's hover expander exists for: it paints the clipped tail in a popup that reaches
        // past the tool window, over the editor. The row is meant to end where the panel ends.
        setExpandableItemsEnabled(false)
    }

    override fun getScrollableTracksViewportWidth(): Boolean = true
}
