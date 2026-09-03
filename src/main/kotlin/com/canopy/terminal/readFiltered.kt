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
        val split = splitTrailingEscape(filter(carried() + raw))
        if (split.emit.length > length) return tooLongForBuffer(buf, offset, length, split, carry)

        carry(split.hold)
        if (split.emit.isEmpty()) continue

        split.emit.toCharArray(buf, offset)
        return split.emit.length
    }
}

private fun tooLongForBuffer(
    buf: CharArray,
    offset: Int,
    length: Int,
    split: SplitOutput,
    carry: (String) -> Unit
): Int {
    carry(split.emit.substring(length) + split.hold)
    split.emit.substring(0, length).toCharArray(buf, offset)

    return length
}
