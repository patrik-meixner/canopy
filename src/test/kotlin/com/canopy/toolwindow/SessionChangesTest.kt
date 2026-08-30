package com.canopy.toolwindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionChangesTest {

    @Test
    fun `an unpushed branch is measured from its base`() {
        assertEquals("main", SessionChanges.commitRangeStart(sharedTip = null, base = "main"))
    }

    @Test
    fun `a tracked branch is measured from what the remote already has`() {
        assertEquals("abc123", SessionChanges.commitRangeStart(sharedTip = "abc123", base = "main"))
    }

    @Test
    fun `without a remote or a base there is nothing to measure from`() {
        assertNull(SessionChanges.commitRangeStart(sharedTip = null, base = null))
    }
}
