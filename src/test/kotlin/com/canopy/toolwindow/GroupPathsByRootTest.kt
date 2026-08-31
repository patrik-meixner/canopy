package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupPathsByRootTest {

    private val roots = listOf("/work/app", "/work/app/libs/dt")

    @Test
    fun `a file in a submodule belongs to the submodule, not the superproject`() {
        val grouped = groupPathsByRoot(listOf("/work/app/libs/dt/src/Table.tsx"), roots)

        assertEquals(mapOf("/work/app/libs/dt" to listOf("/work/app/libs/dt/src/Table.tsx")), grouped)
    }

    @Test
    fun `files split across repositories are committed against their own`() {
        val grouped = groupPathsByRoot(
            listOf("/work/app/README.md", "/work/app/libs/dt/src/Table.tsx"),
            roots
        )

        assertEquals(
            mapOf(
                "/work/app" to listOf("/work/app/README.md"),
                "/work/app/libs/dt" to listOf("/work/app/libs/dt/src/Table.tsx")
            ),
            grouped
        )
    }

    @Test
    fun `a file under no known root is left out rather than guessed at`() {
        val grouped = groupPathsByRoot(listOf("/elsewhere/notes.md"), roots)

        assertEquals(emptyMap<String, List<String>>(), grouped)
    }
}
