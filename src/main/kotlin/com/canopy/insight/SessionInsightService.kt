package com.canopy.insight

import com.canopy.util.ClaudePathEncoder
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps one parsed view of the session currently being looked at, refreshed while a tab is on
 * screen and not otherwise.
 *
 * The transcript is read incrementally, so a tick costs the bytes appended since the last one
 * rather than the whole file, which reaches tens of megabytes on a long session.
 */
@Service(Service.Level.PROJECT)
class SessionInsightService(private val project: Project) : Disposable {

    private val reader = SessionInsightReader()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val listeners = mutableListOf<(SessionInsight) -> Unit>()

    @Volatile private var sessionId: String? = null
    @Volatile private var workingDir: String? = null
    @Volatile private var watchers = 0
    @Volatile private var taskSignature = ""
    @Volatile var insight = SessionInsight()
        private set

    fun addListener(parent: Disposable, listener: (SessionInsight) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
        com.intellij.openapi.util.Disposer.register(parent) {
            synchronized(listeners) { listeners.remove(listener) }
        }
    }

    fun bind(sessionId: String?, workingDir: String?) {
        if (sessionId == this.sessionId && workingDir == this.workingDir) return

        this.sessionId = sessionId
        this.workingDir = workingDir
        reader.reset()
        taskSignature = ""
        insight = SessionInsight()
        refresh()
    }

    /** Ticking with nothing on screen is the cost nobody sees and everybody pays. */
    fun watch() {
        if (watchers++ == 0) schedule()
        refresh()
    }

    fun unwatch() {
        if (watchers > 0) watchers--
    }

    fun refresh() {
        val id = sessionId ?: return
        val transcript = transcriptOf(id) ?: return

        val fresh = reader.read(transcript)
        val signature = TaskStore.signature(id)
        val tasks = if (signature == taskSignature) insight.tasks else TaskStore.read(id)
        taskSignature = signature
        insight = fresh.copy(tasks = tasks)

        val snapshot = insight
        synchronized(listeners) { listeners.toList() }.forEach { it(snapshot) }
    }

    private fun transcriptOf(id: String): Path? {
        val base = workingDir ?: project.basePath ?: return null

        return ClaudePathEncoder.projectDirCandidates(base)
            .map { it.resolve("$id.jsonl") }
            .firstOrNull { Files.exists(it) }
    }

    private fun schedule() {
        if (alarm.isDisposed) return
        alarm.addRequest({
            if (watchers > 0) runCatching { refresh() }
            if (watchers > 0) schedule()
        }, REFRESH_MS)
    }

    override fun dispose() {
        synchronized(listeners) { listeners.clear() }
    }

    companion object {
        private const val REFRESH_MS = 2000

        fun getInstance(project: Project): SessionInsightService =
            project.getService(SessionInsightService::class.java)
    }
}
