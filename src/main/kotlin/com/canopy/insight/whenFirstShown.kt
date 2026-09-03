package com.canopy.insight

import java.awt.event.HierarchyEvent
import javax.swing.JComponent

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
