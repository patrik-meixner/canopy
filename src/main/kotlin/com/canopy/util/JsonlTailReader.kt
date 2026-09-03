package com.canopy.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class JsonlTailReader {

    var offset = 0L
        private set

    fun reset() {
        offset = 0
    }

    fun consume(path: Path, onRestart: () -> Unit = {}, onLine: (String) -> Unit): Int {
        val size = Files.size(path)
        if (size == offset) return 0
        if (size < offset) {
            offset = 0
            onRestart()
        }

        var count = 0
        val leftover = ByteArrayOutputStream()
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            channel.position(offset)
            val buf = ByteBuffer.allocate(CHUNK_BYTES)
            var chunkStart = offset
            while (chunkStart < size) {
                buf.clear()
                val n = channel.read(buf)
                if (n <= 0) break
                buf.flip()
                val bytes = ByteArray(n)
                buf.get(bytes)

                var lineStart = 0
                for (i in 0 until n) {
                    // A newline byte can't occur inside a UTF-8 multi-byte sequence, so
                    // splitting on the raw byte is safe without decoding first.
                    if (bytes[i] != NEWLINE) continue
                    leftover.write(bytes, lineStart, i - lineStart)
                    val line = leftover.toString(Charsets.UTF_8)
                    leftover.reset()
                    lineStart = i + 1
                    offset = chunkStart + lineStart
                    if (line.isNotBlank()) {
                        onLine(line)
                        count++
                    }
                }
                leftover.write(bytes, lineStart, n - lineStart)
                chunkStart += n
            }
        }
        return count
    }

    private companion object {
        const val CHUNK_BYTES = 1 shl 20
        const val NEWLINE = '\n'.code.toByte()
    }
}
