package com.canopy.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class SessionRuntimeService : Disposable {

    private val runtimes = ConcurrentHashMap<String, SessionRuntime>()

    fun existing(key: String): SessionRuntime? = runtimes[key]

    fun all(): List<SessionRuntime> = runtimes.values.toList()

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

    fun keyOf(file: com.canopy.editor.ClaudeSessionVirtualFile): String? =
        runtimes.entries.firstOrNull { it.value.file === file }?.key

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
