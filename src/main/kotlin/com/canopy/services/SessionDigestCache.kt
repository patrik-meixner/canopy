package com.canopy.services

import com.canopy.model.SessionDisplay
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.time.Instant

/**
 * Remembers what each transcript parsed to, keyed on its size and modification time.
 *
 * Transcripts only ever grow, and they grow large: a single project directory here holds well over
 * a gigabyte. Without this every IDE start re-folds all of it to rebuild a list of names and dates.
 */
@State(name = "CanopySessionDigests", storages = [Storage("canopy-sessions.xml")])
@Service(Service.Level.PROJECT)
class SessionDigestCache : PersistentStateComponent<SessionDigestCache.State> {

    class Entry {
        @JvmField var path: String = ""
        @JvmField var size: Long = 0
        @JvmField var modifiedAtMillis: Long = 0
        /** The transcript's own last timestamp, which is what the list sorts on — not the file's. */
        @JvmField var sessionModifiedMillis: Long = 0

        @JvmField var sessionId: String = ""
        @JvmField var name: String? = null
        @JvmField var firstPrompt: String = ""
        @JvmField var messageCount: Int = 0
        @JvmField var gitBranch: String? = null
        @JvmField var worktreeName: String? = null
        @JvmField var startedAtMillis: Long = 0
        @JvmField var touchedRoots: MutableMap<String, Int> = mutableMapOf()
        @JvmField var tail: String? = null
        @JvmField var lastPromptAtMillis: Long = 0
    }

    class State {
        @JvmField var entries: MutableList<Entry> = mutableListOf()
    }

    private var state = State()
    private val byPath = HashMap<String, Entry>()
    /** Entries are written by the parse thread and read by the save thread. */
    private val lock = Any()

    /**
     * The digests worth keeping, which is not the ones being rewritten.
     *
     * An entry is only used again if the transcript's size and timestamp still match, so an entry
     * for a session an agent is working in is guaranteed stale by the next start. Returning it
     * anyway changed this state on every transcript write, and the platform saved the whole file
     * each time: an IDE that never stopped saying it was saving settings.
     *
     * A copy, too: entries are added from the thread that parses transcripts, and handing over the
     * live list let a save walk it while it was being written to.
     */
    override fun getState(): State = synchronized(lock) {
        State().apply { entries = ArrayList(settledEntries(state.entries, System.currentTimeMillis())) }
    }

    override fun loadState(loaded: State) = synchronized(lock) {
        state = loaded
        byPath.clear()
        loaded.entries.forEach {
            // Written before prompts were capped: cut on the way in so the next save is small.
            it.firstPrompt = openingWords(it.firstPrompt)
            byPath[it.path] = it
        }
    }

    fun get(path: String, size: Long, modifiedAtMillis: Long, projectPath: String): SessionDisplay? {
        val entry = synchronized(lock) { byPath[path] } ?: return null
        if (entry.size != size || entry.modifiedAtMillis != modifiedAtMillis) return null

        return SessionDisplay(
            sessionId = entry.sessionId,
            name = entry.name,
            firstPrompt = entry.firstPrompt,
            messageCount = entry.messageCount,
            modified = Instant.ofEpochMilli(entry.sessionModifiedMillis),
            gitBranch = entry.gitBranch,
            projectPath = projectPath,
            worktreeName = entry.worktreeName,
            touchedRoots = entry.touchedRoots,
            startedAt = entry.startedAtMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            tail = entry.tail?.let { runCatching { com.canopy.model.TranscriptTail.valueOf(it) }.getOrNull() },
            lastPromptAt = entry.lastPromptAtMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        )
    }

    fun put(path: String, size: Long, modifiedAtMillis: Long, session: SessionDisplay) = synchronized(lock) {
        val entry = byPath.getOrPut(path) { Entry().also { state.entries.add(it) } }

        entry.path = path
        entry.size = size
        entry.modifiedAtMillis = modifiedAtMillis
        entry.sessionModifiedMillis = session.modified.toEpochMilli()
        entry.sessionId = session.sessionId
        entry.name = session.name
        entry.firstPrompt = openingWords(session.firstPrompt)
        entry.messageCount = session.messageCount
        entry.gitBranch = session.gitBranch
        entry.worktreeName = session.worktreeName
        entry.startedAtMillis = session.startedAt?.toEpochMilli() ?: 0
        entry.touchedRoots = LinkedHashMap(session.touchedRoots)
        entry.tail = session.tail?.name
        entry.lastPromptAtMillis = session.lastPromptAt?.toEpochMilli() ?: 0
    }

    /** Drops entries for transcripts that no longer exist, so the file cannot grow without bound. */
    fun retainOnly(paths: Set<String>) = synchronized(lock) {
        state.entries.removeIf { it.path !in paths }
        byPath.keys.retainAll(paths)
    }

    companion object {
        fun getInstance(project: Project): SessionDigestCache =
            project.getService(SessionDigestCache::class.java)
    }
}
