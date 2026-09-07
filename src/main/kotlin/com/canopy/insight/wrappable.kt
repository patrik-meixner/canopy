package com.canopy.insight

/**
 * A wrapping JTextArea re-reads a whole paragraph for every break it looks for, so one long line
 * lays out slowly enough to freeze the IDE. A line budget alone does not bound that; a character
 * budget does.
 */
fun wrappable(text: String, maxLines: Int, maxCharacters: Int): String {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val kept = lines.take(maxLines).joinToString("\n")
    val clipped = if (kept.length > maxCharacters) kept.take(maxCharacters) + "…" else kept

    return if (lines.size > maxLines) "$clipped\n…" else clipped
}
