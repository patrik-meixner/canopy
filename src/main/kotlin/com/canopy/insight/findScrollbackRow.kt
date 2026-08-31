package com.canopy.insight

/**
 * Which scrollback row a message starts on, searched from the newest backwards.
 *
 * Oldest-first landed on the wrong occurrence whenever a prompt had been repeated, and matching a
 * single row missed any message the terminal had wrapped, which is most of them: a row is joined
 * with the one after it so a key spanning one wrap boundary still matches.
 */
fun findScrollbackRow(lines: List<String>, key: String): Int? {
    if (key.isEmpty()) return null

    return lines.indices.reversed().firstOrNull { row ->
        (lines[row] + lines.getOrElse(row + 1) { "" }).contains(key)
    }
}
