package com.canopy.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Which working tree a directory belongs to, remembered for the life of the IDE.
 *
 * Finding it means walking up to the nearest `.git`, one existence check per level. A session
 * touches thousands of files across a few hundred directories, and the answer for a directory
 * cannot change while the IDE is open, so it is worth asking the filesystem exactly once.
 */
object GitRootCache {

    private val roots = ConcurrentHashMap<Path, String>()
    private val missing = ConcurrentHashMap.newKeySet<Path>()

    fun rootOf(directory: Path): String? {
        roots[directory]?.let { return it }
        if (directory in missing) return null

        val found = walkUp(directory)
        if (found == null) {
            missing.add(directory)
            return null
        }

        roots[directory] = found

        return found
    }

    private fun walkUp(start: Path): String? {
        var current: Path? = start
        while (current != null) {
            roots[current]?.let { return it }
            if (Files.exists(current.resolve(".git"))) return current.toString()

            current = current.parent
        }

        return null
    }
}
