package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Which files each session wrote, for the sessions parsed most recently.
 *
 * Two agents editing the same file is the failure mode that costs an afternoon, and nothing else
 * can see it coming. Deliberately in memory and bounded: a running session's transcript grows, so
 * it is re-parsed on every sweep and its entry stays current, while history would cost a persisted
 * path list per session for a question nobody asks about last month's work.
 */
@Service(Service.Level.PROJECT)
class SessionFileIndex {

    private val paths = object : LinkedHashMap<String, Set<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>) = size > MAX_SESSIONS
    }

    @Synchronized
    fun record(sessionId: String, written: Set<String>) {
        if (written.isEmpty()) return

        paths[sessionId] = written
    }

    @Synchronized
    fun pathsFor(sessionId: String): Set<String> = paths[sessionId].orEmpty()

    @Synchronized
    fun overlapsFor(sessionId: String): Map<String, List<String>> = overlaps(sessionId, LinkedHashMap(paths))

    companion object {
        private const val MAX_SESSIONS = 16

        fun getInstance(project: Project): SessionFileIndex = project.getService(SessionFileIndex::class.java)
    }
}

/** Only files this session wrote can overlap, so the walk is over its own paths, not the whole index. */
internal fun overlaps(sessionId: String, index: Map<String, Set<String>>): Map<String, List<String>> {
    val own = index[sessionId] ?: return emptyMap()
    val others = index.filterKeys { it != sessionId }

    return own.mapNotNull { path ->
        val sharers = others.filterValues { path in it }.keys.toList()
        if (sharers.isEmpty()) null else path to sharers
    }.toMap()
}
