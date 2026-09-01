package com.canopy.services

import com.intellij.util.messages.Topic

/**
 * The session list draws its rows from the open tabs, and the platform fires nothing when a tab's
 * title changes, so the row kept the name the tab no longer had.
 */
fun interface CanopyTabsListener {

    fun tabsChanged()

    companion object {
        val TOPIC: Topic<CanopyTabsListener> = Topic.create("Canopy session tabs", CanopyTabsListener::class.java)
    }
}
