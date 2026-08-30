package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepoScopeDiscoveryTest {

    @Test
    fun `strips the describe suffix from a status line`() {
        val line = "+42b333fbe580fa2fa69b6a9fc403b409efa81472 dmm (2026-08-27-1-16-g42b333fbe)"

        assertEquals("dmm", RepoScopeDiscovery.parseSubmodulePath(line))
    }

    @Test
    fun `keeps a path when no describe suffix is present`() {
        val line = "-97db0e8d313db459563d72868dbda2f4d3fe8b77 saas-documentation"

        assertEquals("saas-documentation", RepoScopeDiscovery.parseSubmodulePath(line))
    }

    @Test
    fun `keeps nested paths reported by recursive status`() {
        val line = " 37654287d509dca0e1b524dab66475cb159f7401 one-frontend/vendor/shared (v1.2.0)"

        assertEquals("one-frontend/vendor/shared", RepoScopeDiscovery.parseSubmodulePath(line))
    }

    @Test
    fun `keeps a trailing parenthesis that is part of the path`() {
        val line = " 37654287d509dca0e1b524dab66475cb159f7401 weird(name)"

        assertEquals("weird(name)", RepoScopeDiscovery.parseSubmodulePath(line))
    }

    @Test
    fun `skips submodules git reports as not checked out`() {
        val status = """
            +42b333fbe580fa2fa69b6a9fc403b409efa81472 dmm (2026-08-27-1-16-g42b333fbe)
            +37654287d509dca0e1b524dab66475cb159f7401 one-frontend (2026-08-27-8-g37654287d5)
            -97db0e8d313db459563d72868dbda2f4d3fe8b77 saas-documentation
        """.trimIndent()

        assertEquals(listOf("dmm", "one-frontend"), RepoScopeDiscovery.initializedSubmodulePaths(status))
    }

    @Test
    fun `returns null when the line carries no path`() {
        val line = "+42b333fbe580fa2fa69b6a9fc403b409efa81472 "

        assertNull(RepoScopeDiscovery.parseSubmodulePath(line))
    }
}
