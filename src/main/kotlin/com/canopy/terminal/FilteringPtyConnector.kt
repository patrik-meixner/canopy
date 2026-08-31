package com.canopy.terminal

import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.pty4j.PtyProcess
import java.nio.charset.Charset

/**
 * Strips the escape sequences JediTerm does not understand, which it otherwise logs as warnings.
 */
open class FilteringPtyConnector(
    process: PtyProcess,
    charset: Charset
) : PtyProcessTtyConnector(process, charset) {

    companion object {
        private val UNSUPPORTED = unsupportedSequences
    }

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val count = super.read(buf, offset, length)
        if (count <= 0) return count

        val raw = String(buf, offset, count)
        val filtered = UNSUPPORTED.replace(raw, "")

        if (filtered.length == count) return count  // nothing stripped

        if (filtered.isEmpty()) return 0

        filtered.toCharArray(buf, offset)
        return filtered.length
    }
}

/**
 * The kitty keyboard pop is `CSI < u`. Matching a bare `CSI u` instead swallows DECRC, which is how
 * a TUI restores a cursor it saved, so the stream loses a sequence the terminal does support.
 */
internal val unsupportedSequences = Regex(
    "\u001b\\[\\?2026[hl]" +
        "|\u001b\\[>\\d+(?:;\\d+)*u" +
        "|\u001b\\[<u" +
        "|\u001b\\[>\\d+(?:;\\d+)*m"
)
