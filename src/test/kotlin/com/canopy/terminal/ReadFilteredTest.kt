package com.canopy.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadFilteredTest {

    private fun readingBack(vararg chunks: String): (CharArray, Int, Int) -> Int {
        val queue = ArrayDeque(chunks.toList())

        return { buf, offset, _ ->
            val next = queue.removeFirstOrNull()
            if (next == null) {
                -1
            } else {
                next.toCharArray(buf, offset)
                next.length
            }
        }
    }

    private fun read(vararg chunks: String): Pair<Int, String> {
        val buf = CharArray(64)
        val count = readFiltered(buf, 0, 64, readingBack(*chunks), { it.replace("DROP", "") }, {})

        return count to String(buf, 0, maxOf(count, 0))
    }

    @Test
    fun `a chunk that filters away entirely is never reported as nothing read`() {
        val (count, text) = read("DROP", "kept")

        assertEquals(4, count)
        assertEquals("kept", text)
    }

    @Test
    fun `a partly filtered chunk keeps what survived`() {
        val (count, text) = read("aDROPb")

        assertEquals(2, count)
        assertEquals("ab", text)
    }

    @Test
    fun `an untouched chunk is passed straight through`() {
        val (count, text) = read("hello")

        assertEquals(5, count)
        assertEquals("hello", text)
    }

    @Test
    fun `a real end of stream is still an end of stream`() {
        val buf = CharArray(8)
        val count = readFiltered(buf, 0, 8, { _, _, _ -> -1 }, { it }, {})

        assertEquals(-1, count)
    }
}
