package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class IgnoredFileCandidatesTest {

    @Test
    fun `local config near the root is offered`() {
        val listed = listOf(".env", "devConfig-local.ts", ".claude/settings.local.json")

        assertEquals(listed, configCandidates(listed))
    }

    @Test
    fun `a collapsed dependency directory is not a config file`() {
        assertEquals(listOf(".env"), configCandidates(listOf("node_modules/", ".env", ".husky/_/")))
    }

    @Test
    fun `build noise is not offered`() {
        val listed = listOf(".DS_Store", ".eslintcache", "yarn-error.log", "tsconfig.tsbuildinfo", ".env")

        assertEquals(listOf(".env"), configCandidates(listed))
    }

    @Test
    fun `generated output buried deep in the tree is not offered`() {
        val listed = listOf(".env", "apps/one-data/locales/app/cs/messages.js")

        assertEquals(listOf(".env"), configCandidates(listed))
    }
}
