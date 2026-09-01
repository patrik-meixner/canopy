package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangesWorkingTreeTest {

    @Test
    fun `a source file is worth a refresh`() {
        assertTrue(changesWorkingTree("/ws/canopy/src/main/kotlin/App.kt"))
    }

    @Test
    fun `what a build wrote is not`() {
        assertFalse(changesWorkingTree("/ws/canopy/build/classes/kotlin/main/App.class"))
        assertFalse(changesWorkingTree("/ws/one-frontend/node_modules/react/index.js"))
        assertFalse(changesWorkingTree("/ws/dmm/target/surefire-reports/x.txt"))
    }

    @Test
    fun `git's own bookkeeping is not, but a commit still is`() {
        assertFalse(changesWorkingTree("/ws/canopy/.git/objects/ab/cdef"))
        assertTrue(changesWorkingTree("/ws/canopy/.git/HEAD"))
        assertTrue(changesWorkingTree("/ws/canopy/.git/index"))
    }

    @Test
    fun `a directory merely named build inside a source path is still a build directory`() {
        assertFalse(changesWorkingTree("/ws/canopy/build/tmp/x"))
    }
}
