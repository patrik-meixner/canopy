package com.canopy.insight

import com.canopy.util.ClaudePathEncoder
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.nio.file.Files
import java.nio.file.Path

/**
 * The parsed view of the session being looked at, refreshed while a tab is on screen and not
 * otherwise.
 *
 * A few sessions are remembered rather than one. Switching used to throw the reader away, so going
 * back to a session you left a moment ago re-read its whole transcript from the top - and flicking
 * between two of them re-read both, every time. What is already known is shown at once and
 * confirmed behind you; only the bytes appended since are ever parsed again.
 */
@Service(Service.Level.PROJECT)
class SessionInsightService(private val project: Project) : Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val listeners = mutableListOf<(SessionInsight) -> Unit>()

    /** Access ordered, so the sessions falling out are the ones nobody has come back to. */
    private val readers = object : LinkedHashMap<String, SessionInsightReader>(REMEMBERED, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, SessionInsightReader>) = size > REMEMBERED
    }
    private val remembered = HashMap<String, SessionInsight>()

    @Volatile private var transcript: Path? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var workingDir: String? = null
    @Volatile private var watchers = 0
    @Volatile private var taskSignature = ""
    @Volatile var insight = SessionInsight()
        private set

    init {
        // Event driven rather than polled: the tab follows the transcript as it is written instead
        // of finding out up to two seconds later. The tick below stays as a backstop for a write
        // the VFS does not report, and costs a stat when nothing has changed.
        project.messageBus.connect(this).subscribe(
            com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
                override fun after(events: List<com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                    val mine = transcript?.toString() ?: return
                    if (watchers > 0 && events.any { it.path == mine }) refreshSoon()
                }
            }
        )
    }

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
        taskSignature = ""
        // Whatever was last known about this session, now, rather than the previous session's
        // content left on screen until a parse finishes.
        insight = sessionId?.let { id -> synchronized(readers) { remembered[id] } } ?: SessionInsight(sessionId)
        publish(insight)
        refreshSoon()
    }

    /** Ticking with nothing on screen is the cost nobody sees and everybody pays. */
    fun watch() {
        if (watchers++ == 0) schedule()
        refreshSoon()
    }

    fun unwatch() {
        if (watchers > 0) watchers--
    }

    fun refresh() {
        val id = sessionId ?: return
        val transcript = transcriptOf(id)?.also { this.transcript = it } ?: return
        val reader = synchronized(readers) { readers.getOrPut(id) { SessionInsightReader() } }

        val fresh = reader.read(transcript)
        val signature = TaskStore.signature(id)
        val tasks = if (signature == taskSignature) insight.tasks else TaskStore.read(id)
        taskSignature = signature
        val updated = fresh.copy(sessionId = id, tasks = tasks)

        synchronized(readers) { remembered[id] = updated }
        // A slow read of the session you just left must not overwrite the one you moved to.
        if (id != sessionId) return

        insight = updated
        publish(updated)
    }

    /**
     * Binding happens on the EDT, and a session bound for the first time is read whole - tens of
     * megabytes on a long one. Done inline it froze the IDE for as long as the parse took.
     */
    private fun refreshSoon() {
        if (alarm.isDisposed) return

        alarm.addRequest({ runCatching { refresh() } }, 0)
    }

    private fun publish(snapshot: SessionInsight) {
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
        synchronized(readers) {
            readers.clear()
            remembered.clear()
        }
    }

    companion object {
        /** Only a backstop now: writes arrive as events, so this exists for the ones that do not. */
        private const val REFRESH_MS = 5000
        private const val REMEMBERED = 4

        fun getInstance(project: Project): SessionInsightService =
            project.getService(SessionInsightService::class.java)
    }
}
