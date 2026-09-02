package com.canopy.toolwindow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsSessionsOwnChangeTest {

    private val written = setOf("/repo/app/Edited.php")

    @Test
    fun `a file the session wrote is its own wherever it lives`() {
        assertTrue(isSessionsOwnChange("/repo/app/Edited.php", written, "/repo/other"))
    }

    @Test
    fun `a file under the session's directory is its own even with no record of writing it`() {
        assertTrue(isSessionsOwnChange("/repo/sub-a/Script.php", emptySet(), "/repo/sub-a"))
    }

    @Test
    fun `a file of the other session in the same repository is not`() {
        assertFalse(isSessionsOwnChange("/repo/sub-b/Other.php", written, "/repo/sub-a"))
    }

    @Test
    fun `a sibling directory sharing a name prefix is not inside it`() {
        assertFalse(isSessionsOwnChange("/repo/sub-a-extra/File.php", emptySet(), "/repo/sub-a"))
    }

    @Test
    fun `with nothing to go on the whole repository is the session's`() {
        assertTrue(isSessionsOwnChange("/repo/app/Other.php", emptySet(), null))
    }

    @Test
    fun `a written record alone is enough to disown the rest`() {
        assertFalse(isSessionsOwnChange("/repo/app/Other.php", written, null))
    }
}
