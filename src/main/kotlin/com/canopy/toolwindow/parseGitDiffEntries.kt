package com.canopy.toolwindow

private const val GITLINK_MODE = "160000"

/**
 * Both sections of `git diff --raw --numstat`, which lists the same files in the same order in
 * each. Git is the one that knows whether anything in a file actually changed: a mode change and
 * a moved submodule both count no lines, and telling them apart needs the modes the raw section
 * carries.
 */
fun parseGitDiffEntries(output: String): List<GitDiffEntry> {
    val lines = output.lines().filter { it.isNotBlank() }
    val raw = lines.filter { it.startsWith(':') }
    val counts = lines.filterNot { it.startsWith(':') }

    return raw.mapIndexed { index, line -> entryOf(line, counts.getOrNull(index)) }
}

private fun entryOf(rawLine: String, countLine: String?): GitDiffEntry {
    val (header, paths) = rawLine.removePrefix(":").split('\t').let { it.first() to it.drop(1) }
    val fields = header.split(' ')
    val oldMode = fields.getOrElse(0) { "" }
    val newMode = fields.getOrElse(1) { "" }
    val status = fields.getOrElse(4) { "M" }.first()
    val oldPath = paths.firstOrNull().orEmpty()
    val isSubmodule = oldMode == GITLINK_MODE || newMode == GITLINK_MODE

    return GitDiffEntry(
        status = status,
        oldPath = oldPath,
        newPath = paths.getOrElse(1) { oldPath },
        isSubmodule = isSubmodule,
        modeChanged = !isSubmodule && oldMode != newMode,
        hasContentChange = countsAsChanged(countLine)
    )
}

/** Git writes "-" instead of a count for a binary, which reads as changed rather than as identical. */
private fun countsAsChanged(countLine: String?): Boolean {
    val counts = countLine?.split('\t') ?: return true

    return counts.getOrNull(0) != "0" || counts.getOrNull(1) != "0"
}
