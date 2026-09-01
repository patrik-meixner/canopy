package com.canopy.services

/**
 * What a tab wants to hear from a session that outlives it.
 *
 * The agent is created once and shown by a succession of editors, so the callbacks cannot be the
 * ones a particular editor handed over at creation: that editor is disposed the moment its tab
 * closes, and the agent would go on driving a dead panel.
 */
interface SessionRuntimeView {

    fun onActiveChanged(isActive: Boolean)

    fun onUserInput()

    fun onUnresponsive()

    fun onResponsive()
}
