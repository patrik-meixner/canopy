package com.canopy.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionInputTest {

    @Test
    fun `a typed reply is submitted`() {
        assertEquals("yes, go ahead\r", replyPayload("yes, go ahead"))
    }

    @Test
    fun `whitespace only is not a reply`() {
        assertNull(replyPayload("   \n "))
    }

    @Test
    fun `a trailing newline does not submit twice`() {
        assertEquals("run it\r", replyPayload("run it\n"))
    }
}
