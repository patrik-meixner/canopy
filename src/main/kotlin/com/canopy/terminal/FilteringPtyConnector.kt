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

    /** Read from and written to by the single reader thread JediTerm runs. */
    private var held = ""
    private val firstOutput = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Runs once the far end has said anything, which for a shell means its prompt is up. */
    var onFirstOutput: (() -> Unit)? = null

    companion object {
        private val UNSUPPORTED = unsupportedSequences(isSynchronizedOutputSupported())
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
        onRaw = {
            trace?.output(it)
            if (!firstOutput.getAndSet(true)) onFirstOutput?.invoke()
        },
        carried = { held.also { held = "" } },
        carry = { held = it }
    )
}
