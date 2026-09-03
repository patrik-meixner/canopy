package com.canopy.terminal

data class SplitOutput(val emit: String, val hold: String)

private const val ESCAPE = ''
private const val BELL = ''
private const val LONGEST_SEQUENCE = 64
private const val STRING_INTRODUCERS = "]PX^_"

fun splitTrailingEscape(text: String): SplitOutput {
    val start = text.lastIndexOf(ESCAPE)
    if (start < 0) return SplitOutput(text, "")

    val tail = text.substring(start)
    if (tail.length > LONGEST_SEQUENCE || isComplete(tail)) return SplitOutput(text, "")

    return SplitOutput(text.substring(0, start), tail)
}

/**
 * A CSI ends on a byte in `@`..`~`; the string sequences end on BEL or ST. Anything that is not an
 * introducer at all is a two-character sequence, complete as soon as it has both.
 */
private fun isComplete(tail: String): Boolean {
    if (tail.length < 2) return false
    val introducer = tail[1]

    if (introducer == '[') return tail.drop(2).any { it in '@'..'~' }
    if (introducer in STRING_INTRODUCERS) return tail.contains(BELL) || tail.contains("$ESCAPE\\")

    return true
}
