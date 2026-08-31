package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMetaLineTest {

    private fun session(worktree: String? = null, project: String = "/Users/me/projects/canopy") = SessionDisplay(
        sessionId = "s1",
        name = "fix the build",
        firstPrompt = "fix",
        messageCount = 3,
        modified = Instant.now(),
        gitBranch = "main",
        projectPath = project,
        worktreeName = worktree
    )

    @Test
    fun `a worktree session says which worktree, not which project`() {
        val line = metaLine(session(worktree = "auth-refactor"), "")

        assertTrue(line.startsWith("auth-refactor"))
        assertFalse(line.contains("canopy"))
    }

    @Test
    fun `a plain session says the project directory, not its whole path`() {
        val line = metaLine(session(), "")

        assertTrue(line.startsWith("canopy"))
        assertFalse(line.contains("/Users"))
    }

    @Test
    fun `live figures are appended, not substituted for the place`() {
        val line = metaLine(session(), "26%  \$23.81")

        assertTrue(line.startsWith("canopy"))
        assertTrue(line.endsWith("26%  \$23.81"))
    }

    @Test
    fun `age reads in the unit that fits`() {
        val now = 1_000_000_000L

        assertEquals("just now", relativeAge(now - 30_000, now))
        assertEquals("5m ago", relativeAge(now - 5 * 60_000, now))
        assertEquals("3h ago", relativeAge(now - 3 * 3_600_000, now))
        assertEquals("2d ago", relativeAge(now - 2 * 86_400_000, now))
    }

    @Test
    fun `an idle session shows no empty trailing column`() {
        assertFalse(metaLine(session(), "").trim().endsWith("   "))
    }
}

class SessionPlacesTest {

    private fun session(touched: Map<String, Int>, worktree: String? = null) = SessionDisplay(
        sessionId = "s1",
        name = "s",
        firstPrompt = "p",
        messageCount = 1,
        modified = Instant.now(),
        gitBranch = null,
        projectPath = "/Users/me/projects/urbido-2.0",
        worktreeName = worktree,
        touchedRoots = touched
    )

    @Test
    fun `a session says where it wrote, not where it was launched`() {
        val places = placesFor(session(mapOf("/Users/me/projects/canopy" to 40)))

        assertEquals("canopy", places)
    }

    @Test
    fun `the most written repository leads`() {
        val places = placesFor(session(mapOf("/ws/dmm" to 3, "/ws/one-frontend" to 30)))

        assertEquals("one-frontend, dmm", places)
    }

    @Test
    fun `many repositories are counted rather than listed, so the card cannot widen`() {
        val touched = mapOf("/ws/a" to 5, "/ws/b" to 4, "/ws/c" to 3, "/ws/d" to 2)

        assertEquals("a, b +2", placesFor(session(touched)))
    }

    @Test
    fun `a session that recorded nothing falls back to its worktree`() {
        assertEquals("auth-refactor", placesFor(session(emptyMap(), worktree = "auth-refactor")))
    }

    @Test
    fun `and to the project when there is no worktree either`() {
        assertEquals("urbido-2.0", placesFor(session(emptyMap())))
    }

    @Test
    fun `the project is never repeated next to the repositories`() {
        val line = metaLine(session(mapOf("/ws/dmm" to 2)), "40%")

        assertEquals(1, line.split("dmm").size - 1)
        assertTrue(!line.contains("urbido-2.0"))
    }
}
