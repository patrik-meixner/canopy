package com.canopy.insight

import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel

/**
 * A list whose rows are as wide as the viewport, never as wide as their content.
 *
 * A plain JList reports the widest cell as its preferred width, so a card that wraps its text to
 * the list's current width grows the list, which widens the cards, which grows the list: the row
 * spills past the edge and a horizontal scrollbar appears under it.
 */
class ViewportWidthList<T>(model: DefaultListModel<T>) : JBList<T>(model) {

    init {
        // Rows are deliberately narrower than their content here, which is exactly the case the
        // platform's hover expander exists for: it paints the clipped tail in a popup that reaches
        // past the tool window, over the editor. The row is meant to end where the panel ends.
        setExpandableItemsEnabled(false)
    }

    override fun getScrollableTracksViewportWidth(): Boolean = true
}
