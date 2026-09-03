package com.canopy.insight

fun findScrollbackRow(lines: List<String>, key: String): Int? {
    if (key.isEmpty()) return null

    return lines.indices.reversed().firstOrNull { row ->
        (lines[row] + lines.getOrElse(row + 1) { "" }).contains(key)
    }
}
