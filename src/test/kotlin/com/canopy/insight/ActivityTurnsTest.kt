package com.canopy.insight

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityTurnsTest {

    private val entries = listOf(
        ActivityEntry("Read", "/ws/a.kt", 100, turn = 1),
        ActivityEntry("Grep", "todo", 110, turn = 1),
        ActivityEntry("Edit", "/ws/a.kt", 120, turn = 1),
        ActivityEntry("Bash", "./gradlew test", 130, turn = 1),
        ActivityEntry("Read", "/ws/b.kt", 200, turn = 2)
    )

    private val prompts = mapOf(1 to "fix the build", 2 to "what does b do")

    @Test
    fun `a turn is what happened between two of your prompts`() {
        val turns = activityTurns(entries, prompts, writesOnly = false)

        assertEquals(listOf(1, 2), turns.map { it.ordinal })
        assertEquals(listOf("fix the build", "what does b do"), turns.map { it.prompt })
        assertEquals(100, turns.first().atMillis)
    }

    @Test
    fun `a turn counts what it wrote, what it ran and what it looked at`() {
        val turn = activityTurns(entries, prompts, writesOnly = false).first()

        assertEquals(listOf("/ws/a.kt"), turn.filesWritten)
        assertEquals(1, turn.commandsRun)
        assertEquals(2, turn.lookups)
        assertEquals(listOf("gradlew"), turn.commandVerbs)
        assertEquals("1 file written   1 command (gradlew)   2 lookups", turnSummary(turn))
    }

    @Test
    fun `a turn that wrote nothing is not a change to review`() {
        val turns = activityTurns(entries, prompts, writesOnly = true)

        assertEquals(listOf(1), turns.map { it.ordinal })
    }

    @Test
    fun `work before your first prompt is its own turn, not attributed to one`() {
        val turns = activityTurns(listOf(ActivityEntry("Read", "/ws/a.kt", 10, turn = 0)), prompts, writesOnly = false)

        assertEquals(listOf(0), turns.map { it.ordinal })
        assertEquals("", turns.single().prompt)
    }
}
