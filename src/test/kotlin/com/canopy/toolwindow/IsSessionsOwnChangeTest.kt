package com.canopy.toolwindow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsSessionsOwnChangeTest {

    private val started = 1_000L
    private val before = 500L
    private val after = 1_500L
    private val written = setOf("/repo/app/Edited.php")

    private fun own(path: String, directories: List<String>, modified: Long?, recorded: Set<String> = written) =
        isSessionsOwnChange(path, recorded, directories, started) { modified }

    @Test
    fun `a file the session wrote is its own wherever it lives and whenever it changed`() {
        assertTrue(own("/repo/app/Edited.php", listOf("/repo/other"), before))
    }

    @Test
    fun `a file changed under the session's directory after it began is its own`() {
        assertTrue(own("/repo/sub-a/Script.php", listOf("/repo/sub-a"), after, emptySet()))
    }

    @Test
    fun `a file already dirty when the session was launched there is not`() {
        assertFalse(own("/repo/www/assets/js/billing-rules.js", listOf("/repo"), before, emptySet()))
    }

    @Test
    fun `a file of the other session in the same repository is not`() {
        assertFalse(own("/repo/sub-b/Other.php", listOf("/repo/sub-a"), after))
    }

    @Test
    fun `a repository the session changed into and wrote in counts as its directory`() {
        assertTrue(own("/other/src/X.kt", listOf("/launch", "/other"), after, emptySet()))
    }

    @Test
    fun `a sibling directory sharing a name prefix is not inside it`() {
        assertFalse(own("/repo/sub-a-extra/File.php", listOf("/repo/sub-a"), after, emptySet()))
    }

    @Test
    fun `a file whose date cannot be read is given the benefit of the doubt`() {
        assertTrue(own("/repo/sub-a/Gone.php", listOf("/repo/sub-a"), null, emptySet()))
    }

    @Test
    fun `with nothing to go on the whole repository is the session's`() {
        assertTrue(own("/repo/app/Other.php", emptyList(), before, emptySet()))
    }
}
