package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path

fun sessionFootprint(searchIn: List<Path>, tasksRoot: Path, sessionId: String): List<Path> {
    val inProjects = searchIn.filter { Files.isDirectory(it) }.flatMap { directory ->
        listOf(directory.resolve("$sessionId.jsonl"), directory.resolve(sessionId))
    }

    return (inProjects + listOf(tasksRoot.resolve(sessionId))).filter { Files.exists(it) }
}
