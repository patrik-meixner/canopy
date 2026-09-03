package com.canopy.services

interface SessionRuntimeView {

    fun onActiveChanged(isActive: Boolean)

    fun onUserInput()

    fun onUnresponsive()

    fun onResponsive()
}
