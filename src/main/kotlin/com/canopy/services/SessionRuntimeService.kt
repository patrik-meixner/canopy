package com.canopy.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps every agent alive for as long as the project is, so closing a tab stops being the same
 * thing as killing what it was showing.
 */
@Service(Service.Level.PROJECT)
class SessionRuntimeService : Disposable {

    private val runtimes = ConcurrentHashMap<String, SessionRuntime>()

    fun existing(key: String): SessionRuntime? = runtimes[key]

    /** Every agent the project is holding, whether or not a tab is showing it. */
    fun all(): List<SessionRuntime> = runtimes.values.toList()

    /**
     * The view is handed to the caller before the process exists, because output can arrive while
     * the terminal is still being built.
     */
    fun create(
        key: String,
        file: com.canopy.editor.ClaudeSessionVirtualFile,
        start: (Disposable, SessionRuntimeView) -> TerminalSession
    ): SessionRuntime {
        val disposable = Disposer.newDisposable(this, "canopy-runtime-$key")
        val runtime = SessionRuntime(file, disposable)
        runtime.session = start(disposable, runtime)
        runtimes[key] = runtime

        return runtime
    }

    /** A session started inside Canopy is keyed on its tab until Claude gives it a real id. */
    fun rekey(from: String, to: String) {
        if (from == to) return

        runtimes.remove(from)?.let { runtimes[to] = it }
    }

    fun stop(key: String) {
        runtimes.remove(key)?.let { Disposer.dispose(it.disposable) }
    }

    override fun dispose() = runtimes.clear()

    companion object {
        fun getInstance(project: Project): SessionRuntimeService =
            project.getService(SessionRuntimeService::class.java)
    }
}
