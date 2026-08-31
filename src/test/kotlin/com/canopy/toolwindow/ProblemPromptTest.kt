package com.canopy.toolwindow

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemPromptTest {

    private val roots = setOf("/ws")

    @Test
    fun `each problem carries a location the agent can open`() {
        val prompt = problemPrompt(listOf(FileProblem("/ws/App.kt", 42, "Unresolved reference")), roots)

        assertTrue(prompt!!.contains("- App.kt:42 Unresolved reference"))
    }

    @Test
    fun `a clean change asks for nothing`() {
        assertNull(problemPrompt(emptyList(), roots))
    }

    @Test
    fun `a flood is cut and counted rather than pasted whole`() {
        val problems = (1..40).map { FileProblem("/ws/App.kt", it, "problem $it") }
        val prompt = problemPrompt(problems, roots)!!

        assertTrue(prompt.contains("and 15 more"))
        assertTrue(prompt.lines().size <= 28)
    }

    @Test
    fun `the prompt tells the agent what to do with them`() {
        val prompt = problemPrompt(listOf(FileProblem("/ws/App.kt", 1, "x")), roots)!!

        assertTrue(prompt.startsWith("The IDE reports these problems"))
        assertTrue(prompt.endsWith("\r"))
    }
}
