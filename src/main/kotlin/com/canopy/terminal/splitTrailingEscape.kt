package com.canopy.terminal

/** What can be handed on now, and the start of an escape sequence that has not finished arriving. */
data class SplitOutput(val emit: String, val hold: String)

private const val ESCAPE = ''
private const val BELL = ''
private const val LONGEST_SEQUENCE = 64
private const val STRING_INTRODUCERS = "]PX^_"

/**
 * Holds back an escape sequence a read stopped in the middle of.
 *
 * A pty read ends wherever the buffer fills, which is regularly halfway through an escape sequence.
 * The filter matches whole sequences, so a split one matched nothing, went out unfiltered, and the
 * half the terminal could not parse was drawn as text - `ESC[?25l` in the middle of a prompt.
 *
 * Only a plausible sequence is held: a long tail with no terminator is text that happens to contain
 * an escape, and holding it would stall the terminal.
 */
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
