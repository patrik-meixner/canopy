package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeNoteTest {

    private fun entry(isSubmodule: Boolean = false, modeChanged: Boolean = false, content: Boolean = true) =
        GitDiffEntry('M', "a", "a", isSubmodule, modeChanged, content)

    @Test
    fun `a file whose content changed needs no note`() {
        assertNull(changeNote(entry()))
    }

    @Test
    fun `a file that only changed permissions says so, because its diff is empty`() {
        assertEquals("permissions only", changeNote(entry(modeChanged = true, content = false)))
    }

    @Test
    fun `a submodule says it moved rather than claiming its permissions changed`() {
        assertEquals("submodule", changeNote(entry(isSubmodule = true, content = false)))
    }

    @Test
    fun `permissions changed alongside real edits is not worth a note`() {
        assertNull(changeNote(entry(modeChanged = true, content = true)))
    }
}
