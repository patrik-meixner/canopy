package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextFrontmatterTest {

    @Test
    fun `name and description come from the frontmatter block`() {
        val lines = listOf("---", "name: using-superpowers", "description: Use when starting", "---", "# Body")

        assertEquals("using-superpowers" to "Use when starting", frontmatterOf(lines))
    }

    @Test
    fun `a file without frontmatter reports nothing rather than its first line`() {
        assertEquals(null to null, frontmatterOf(listOf("# Written output", "name: not-frontmatter")))
    }

    @Test
    fun `a name below the closing delimiter is body text, not frontmatter`() {
        val lines = listOf("---", "description: real", "---", "name: fake")

        assertNull(frontmatterOf(lines).first)
    }

    @Test
    fun `quotes around a value are not part of it`() {
        assertEquals("Deploy steps", frontmatterOf(listOf("---", "description: \"Deploy steps\"", "---")).second)
    }
}
