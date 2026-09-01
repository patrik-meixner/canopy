package com.canopy.services

import com.intellij.openapi.Disposable

/** An agent and its terminal, owned by the project rather than by whichever tab is showing them. */
class SessionRuntime internal constructor(
    val file: com.canopy.editor.ClaudeSessionVirtualFile,
    val disposable: Disposable
) : SessionRuntimeView {

    lateinit var session: TerminalSession
        internal set

    @Volatile
    var view: SessionRuntimeView? = null

    /** Typed into the agent as if from its terminal, whether or not a tab is showing one. */
    fun sendInput(text: String) {
        val process = session.process
        if (!process.isAlive) return

        try {
            process.outputStream.write(text.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
        } catch (_: java.io.IOException) {
        }
    }

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
