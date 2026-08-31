package com.canopy.terminal

import com.pty4j.PtyProcess
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Writes everything a session's terminal sends and receives to a file, for when a terminal behaves
 * differently here than it does anywhere else and reasoning about it has run out.
 *
 * Off unless `-Dcanopy.pty.trace=true` is set, because it records everything typed.
 */
class PtyTrace private constructor(private val file: Path) {

    fun output(text: String) = append("< ", text)

    fun input(text: String) = append("> ", text)

    private fun append(direction: String, text: String) {
        runCatching {
            Files.writeString(file, direction + visible(text) + "\n", StandardOpenOption.APPEND)
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

            return runCatching {
                Files.writeString(file, "")
                PtyTrace(file)
            }.getOrNull()
        }
    }
}
