package com.canopy.services

import com.intellij.openapi.Disposable

/** An agent and its terminal, owned by the project rather than by whichever tab is showing them. */
class SessionRuntime internal constructor(val disposable: Disposable) : SessionRuntimeView {

    lateinit var session: TerminalSession
        internal set

    @Volatile
    var view: SessionRuntimeView? = null

    override fun onActiveChanged(isActive: Boolean) {
        view?.onActiveChanged(isActive)
    }

    override fun onUserInput() {
        view?.onUserInput()
    }

    override fun onUnresponsive() {
        view?.onUnresponsive()
    }

    override fun onResponsive() {
        view?.onResponsive()
    }
}
