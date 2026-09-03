package com.canopy.services

import com.intellij.util.messages.Topic

fun interface CanopyTabsListener {

    fun tabsChanged()

    companion object {
        val TOPIC: Topic<CanopyTabsListener> = Topic.create("Canopy session tabs", CanopyTabsListener::class.java)
    }
}
