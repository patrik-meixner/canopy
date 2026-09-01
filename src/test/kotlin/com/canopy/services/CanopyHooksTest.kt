package com.canopy.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanopyHooksTest {

    private val hooks = canopyHooks("/tmp/notify.sh")

    @Test
    fun `a turn opens on a prompt and closes on stop`() {
        assertTrue(hooks.has("UserPromptSubmit"))
        assertTrue(hooks.has("Stop"))
    }

    @Test
    fun `a tool finishing is not the end of a turn`() {
        assertFalse(hooks.has("PostToolUse"))
        assertFalse(hooks.has("PostToolUseFailure"))
    }

    @Test
    fun `a subagent finishing is not the end of a turn either`() {
        assertFalse(hooks.has("SubagentStop"))
    }

    @Test
    fun `every tool re-asserts the turn, whoever started it`() {
        val rules = hooks.getAsJsonArray("PreToolUse")

        assertEquals(1, rules.size())
        assertEquals(".*", rules[0].asJsonObject.get("matcher").asString)
    }

    @Test
    fun `what needs a human is reported apart from what the agent is doing`() {
        val matchers = hooks.getAsJsonArray("Notification").map { it.asJsonObject.get("matcher").asString }

        assertEquals(listOf("idle_prompt", "permission_prompt"), matchers)
    }

    @Test
    fun `every hook runs the script it was given`() {
        val commands = hooks.keySet().flatMap { event ->
            hooks.getAsJsonArray(event).flatMap { rule ->
                rule.asJsonObject.getAsJsonArray("hooks").map { it.asJsonObject.get("command").asString }
            }
        }

        assertTrue(commands.isNotEmpty())
        assertTrue(commands.all { it == "/tmp/notify.sh" })
    }
}
