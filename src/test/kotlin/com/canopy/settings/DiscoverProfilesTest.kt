package com.canopy.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverProfilesTest {

    @Test
    fun `the plain config directory is the default profile`() {
        assertEquals(
            listOf(ClaudeProfile("Default", ".claude")),
            discoverProfiles(listOf(".claude", "Documents", ".zshrc"))
        )
    }

    @Test
    fun `a suffixed config directory is a profile named after its suffix`() {
        assertEquals(
            listOf(ClaudeProfile("Default", ".claude"), ClaudeProfile("Personal", ".claude-personal")),
            discoverProfiles(listOf(".claude-personal", ".claude"))
        )
    }

    @Test
    fun `the default comes first and the rest read in order`() {
        assertEquals(
            listOf("Default", "Personal", "Work"),
            discoverProfiles(listOf(".claude-work", ".claude-personal", ".claude")).map { it.name }
        )
    }

    @Test
    fun `a home with no config directory offers no profiles`() {
        assertEquals(emptyList(), discoverProfiles(listOf("Documents", ".claudia", "claude")))
    }

    @Test
    fun `a suffix of several words keeps them all`() {
        assertEquals("Side project", discoverProfiles(listOf(".claude-side-project")).single().name)
    }
}
