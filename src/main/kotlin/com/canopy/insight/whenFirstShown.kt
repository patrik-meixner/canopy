package com.canopy.insight

import java.awt.event.HierarchyEvent
import javax.swing.JComponent

/**
 * Work that waits until there is a reason to do it.
 *
 * A list of forty messages builds forty cards whether or not you scroll to the fifth, and a card
 * holding a screenshot costs a decode. Hanging that on the component becoming visible means the
 * ones nobody looks at cost nothing at all.
 */
fun whenFirstShown(component: JComponent, work: () -> Unit) {
    if (component.isShowing) return work()

    component.addHierarchyListener(object : java.awt.event.HierarchyListener {
        override fun hierarchyChanged(event: HierarchyEvent) {
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return
            if (!component.isShowing) return

            component.removeHierarchyListener(this)
            work()
        }
    })
}
