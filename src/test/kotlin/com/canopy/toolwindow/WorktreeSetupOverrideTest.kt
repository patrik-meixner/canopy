package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class WorktreeSetupOverrideTest {

    @Test
    fun `a repository without an entry falls back to the shared default`() {
        assertEquals(".env", setupFilesFor(emptyMap(), "one-frontend", ".env"))
    }

    @Test
    fun `a submodule's own list wins over the default`() {
        val overrides = mapOf("one-frontend" to "devConfig-local.ts,.env")

        assertEquals("devConfig-local.ts\n.env", setupFilesFor(overrides, "one-frontend", ".env"))
    }

    @Test
    fun `a repository configured to copy nothing copies nothing`() {
        assertEquals("", setupFilesFor(mapOf("dmm" to ""), "dmm", ".env"))
    }

    @Test
    fun `a repository's own install command wins over the default`() {
        assertEquals("pnpm install", setupCommandFor(mapOf("one-frontend" to "pnpm install"), "one-frontend", "npm ci"))
    }
}
