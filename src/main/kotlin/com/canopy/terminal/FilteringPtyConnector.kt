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

    private val trace = PtyTrace.of(process)

    companion object {
        private val UNSUPPORTED = unsupportedSequences
    }

    override fun write(bytes: ByteArray) {
        trace?.input(String(bytes, Charsets.UTF_8))
        super.write(bytes)
    }

    override fun write(string: String) {
        trace?.input(string)
        super.write(string)
    }

    override fun read(buf: CharArray, offset: Int, length: Int): Int = readFiltered(
        buf,
        offset,
        length,
        readOnce = { target, from, size -> super.read(target, from, size) },
        filter = { UNSUPPORTED.replace(it, "") },
        onRaw = { trace?.output(it) }
    )
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
