package com.canopy.terminal

/**
 * Reads until something survives the filter, because a zero-length read ends the session.
 *
 * JediTerm's data stream throws EOF on any read that does not return a positive count, and the
 * agent emits lone synchronized-output markers often enough that a whole chunk being filtered away
 * is routine rather than rare. Reporting that as nothing read killed a running terminal at random.
 */
internal fun readFiltered(
    buf: CharArray,
    offset: Int,
    length: Int,
    readOnce: (CharArray, Int, Int) -> Int,
    filter: (String) -> String,
    onRaw: (String) -> Unit,
    carried: () -> String = { "" },
    carry: (String) -> Unit = {}
): Int {
    while (true) {
        val count = readOnce(buf, offset, length)
        if (count <= 0) return count

        val raw = String(buf, offset, count)
        onRaw(raw)
        // Joined before filtering, not after: the whole point is that the filter sees the sequence
        // whole rather than two halves, neither of which it matches.
        val split = splitTrailingEscape(filter(carried() + raw))
        carry(split.hold)
        if (split.emit.isEmpty()) continue
        if (split.emit.length > length) return tooLongForBuffer(buf, offset, length, split.emit, carry)

        split.emit.toCharArray(buf, offset)
        return split.emit.length
    }
}

/**
 * A held-back tail plus a full read can exceed the buffer the caller offered, and writing past it
 * would corrupt whatever sits after. The rest goes back into the carry and arrives next read.
 */
private fun tooLongForBuffer(
    buf: CharArray,
    offset: Int,
    length: Int,
    text: String,
    carry: (String) -> Unit
): Int {
    carry(text.substring(length))
    text.substring(0, length).toCharArray(buf, offset)

    return length
}
