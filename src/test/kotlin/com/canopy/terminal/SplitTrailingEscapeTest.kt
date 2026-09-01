package com.canopy.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

private const val ESC = '\u001b'
private const val BEL = '\u0007'

class SplitTrailingEscapeTest {

    @Test
    fun `plain text goes out whole`() {
        assertEquals(SplitOutput("hello", ""), splitTrailingEscape("hello"))
    }

    @Test
    fun `a finished sequence goes out whole`() {
        val text = "a$ESC[?25l"

        assertEquals(SplitOutput(text, ""), splitTrailingEscape(text))
    }

    @Test
    fun `a sequence the read stopped inside is held back`() {
        assertEquals(SplitOutput("a", "$ESC[?25"), splitTrailingEscape("a$ESC[?25"))
    }

    @Test
    fun `a lone escape at the end is held back`() {
        assertEquals(SplitOutput("done", "$ESC"), splitTrailingEscape("done$ESC"))
    }

    @Test
    fun `an unterminated string sequence is held back`() {
        assertEquals(SplitOutput("x", "$ESC]0;title"), splitTrailingEscape("x$ESC]0;title"))
    }

    @Test
    fun `a terminated string sequence goes out whole`() {
        val text = "x$ESC]0;title$BEL"

        assertEquals(SplitOutput(text, ""), splitTrailingEscape(text))
    }

    @Test
    fun `an escape buried in a wall of text is not held, or the terminal would stall`() {
        val text = "$ESC" + "x".repeat(200)

        assertEquals(SplitOutput(text, ""), splitTrailingEscape(text))
    }
}

class CarriedSequenceTest {

    /** The whole point: a sequence split across two reads must still be filtered, not passed on. */
    @Test
    fun `a filtered sequence split across two reads never reaches the terminal`() {
        val chunks = listOf("hi$ESC[?20", "26h there").iterator()
        val buffer = CharArray(64)
        var held = ""
        val filter = Regex("$ESC\\[\\?2026[hl]")
        val out = StringBuilder()

        repeat(2) {
            val read = readFiltered(
                buffer, 0, buffer.size,
                readOnce = { target, from, _ ->
                    val next = chunks.next()
                    next.toCharArray(target, from)
                    next.length
                },
                filter = { filter.replace(it, "") },
                onRaw = {},
                carried = { held.also { held = "" } },
                carry = { held = it }
            )
            out.append(String(buffer, 0, read))
        }

        assertEquals("hi there", out.toString())
    }
}
