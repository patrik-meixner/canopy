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
    onRaw: (String) -> Unit
): Int {
    while (true) {
        val count = readOnce(buf, offset, length)
        if (count <= 0) return count

        val raw = String(buf, offset, count)
        onRaw(raw)
        val filtered = filter(raw)

        if (filtered.length == count) return count
        if (filtered.isEmpty()) continue

        filtered.toCharArray(buf, offset)
        return filtered.length
    }
}
