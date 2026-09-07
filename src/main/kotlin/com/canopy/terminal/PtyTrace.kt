package com.canopy.terminal

import com.pty4j.PtyProcess
import java.io.BufferedWriter
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

class PtyTrace(file: Path) : Closeable {

    private val writer: BufferedWriter = Files.newBufferedWriter(file)

    fun output(text: String) = append("< ", text)

    fun input(text: String) = append("> ", text)

    override fun close() {
        runCatching { writer.close() }
    }

    private fun append(direction: String, text: String) {
        runCatching {
            writer.write(direction + visible(text) + "\n")
            writer.flush()
        }
    }

    private fun visible(text: String) = text
        .replace("\u001b", "<ESC>")
        .replace("\r", "<CR>")
        .replace("\n", "<LF>")

    companion object {
        fun of(process: PtyProcess): PtyTrace? {
            if (System.getProperty("canopy.pty.trace") != "true") return null

            val file = Path.of(System.getProperty("java.io.tmpdir"), "canopy-pty-${process.pid()}.log")

            return runCatching { PtyTrace(file) }.getOrNull()
        }
    }
}
