package com.canopy.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

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

internal fun overlaps(sessionId: String, index: Map<String, Set<String>>): Map<String, List<String>> {
    val own = index[sessionId] ?: return emptyMap()
    val others = index.filterKeys { it != sessionId }

    return own.mapNotNull { path ->
        val sharers = others.filterValues { path in it }.keys.toList()
        if (sharers.isEmpty()) null else path to sharers
    }.toMap()
}
