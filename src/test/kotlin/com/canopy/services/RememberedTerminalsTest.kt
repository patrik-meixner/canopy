package com.canopy.services

import org.junit.Assert.assertEquals
import org.junit.Test

class RememberedTerminalsTest {

    private fun session(key: String, id: String?) = OpenTab(key, id, isShell = false, ownerKey = null, name = key)

    private fun shell(name: String, ownerKey: String?) =
        OpenTab("shell-$name", null, isShell = true, ownerKey = ownerKey, name = name)

    @Test
    fun `a shell is remembered against its session's id, not its tab key`() {
        val remembered = rememberedTerminals(listOf(session("new-7", "abc-123"), shell("be", "new-7")))

        assertEquals(listOf(RememberedTerminal("abc-123", "be")), remembered)
    }

    @Test
    fun `a shell whose session never started is dropped`() {
        assertEquals(emptyList<RememberedTerminal>(), rememberedTerminals(listOf(session("new-7", null), shell("be", "new-7"))))
    }

    @Test
    fun `a loose shell belonging to nothing is dropped`() {
        assertEquals(emptyList<RememberedTerminal>(), rememberedTerminals(listOf(session("a", "abc"), shell("be", null))))
    }

    @Test
    fun `the name a shell was renamed to is what gets remembered`() {
        val remembered = rememberedTerminals(listOf(session("a", "abc"), shell("frontend dev", "a"), shell("logs", "a")))

        assertEquals(listOf("frontend dev", "logs"), remembered.map { it.name })
    }
}
