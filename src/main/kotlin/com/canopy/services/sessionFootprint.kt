package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path

/**
 * Everything on disk that makes a session exist.
 *
 * Deleting only the transcript under the one directory the encoder happens to produce first left
 * the file where Claude Code actually wrote it, so the next refresh read the session back in. A
 * session is gone when nothing can reconstruct it: its transcript, whatever it stored beside it,
 * and its task list.
 */
fun sessionFootprint(searchIn: List<Path>, tasksRoot: Path, sessionId: String): List<Path> {
    val inProjects = searchIn.filter { Files.isDirectory(it) }.flatMap { directory ->
        listOf(directory.resolve("$sessionId.jsonl"), directory.resolve(sessionId))
    }

    // Path is itself an Iterable of its name elements, so `list + path` appends those elements
    // one by one rather than the path.
    return (inProjects + listOf(tasksRoot.resolve(sessionId))).filter { Files.exists(it) }
}
