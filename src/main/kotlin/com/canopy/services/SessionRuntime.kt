package com.canopy.services

import com.intellij.openapi.Disposable

class SessionRuntime internal constructor(
    val file: com.canopy.editor.ClaudeSessionVirtualFile,
    val disposable: Disposable
) : SessionRuntimeView {

    lateinit var session: TerminalSession
        internal set

    @Volatile
    var view: SessionRuntimeView? = null

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
