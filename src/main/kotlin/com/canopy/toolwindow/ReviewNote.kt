package com.canopy.toolwindow

import java.nio.file.Path

data class ReviewNote(
    val paths: List<String>,
    val lines: IntRange?,
    val note: String
)

private const val MAX_LISTED_PATHS = 12

fun promptFor(note: ReviewNote, repositoryRoots: Set<String>): String? {
    val text = note.note.trim()
    if (text.isEmpty() || note.paths.isEmpty()) return null

    val listed = note.paths.take(MAX_LISTED_PATHS).map { relativize(it, repositoryRoots) }
    val rest = note.paths.size - listed.size
    val where = when {
        listed.size == 1 && note.lines != null -> "${listed.single()}:${note.lines.first}-${note.lines.last}"
        listed.size == 1 -> listed.single()
        rest > 0 -> listed.joinToString(", ") + " and $rest more"
        else -> listed.joinToString(", ")
    }

    return "In $where: $text\r"
}

internal fun relativize(path: String, repositoryRoots: Set<String>): String {
    val normalized = Path.of(path).normalize()
    val root = repositoryRoots
        .map { Path.of(it).normalize() }
        .filter { normalized.startsWith(it) }
        .maxByOrNull { it.nameCount }
        ?: return normalized.fileName?.toString() ?: path

    return root.relativize(normalized).toString()
}
