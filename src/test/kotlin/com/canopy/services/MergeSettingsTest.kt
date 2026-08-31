package com.canopy.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeSettingsTest {

    private fun json(text: String): JsonObject = Gson().fromJson(text, JsonObject::class.java)

    @Test
    fun `settings the user configured survive`() {
        val merged = mergeSettings(json("""{"model":"opus","env":{"FOO":"1"}}"""), json("""{"statusLine":{"type":"command"}}"""))

        assertEquals("opus", merged.get("model").asString)
        assertEquals("1", merged.getAsJsonObject("env").get("FOO").asString)
    }

    @Test
    fun `the status line is ours, because that is what we are there to replace`() {
        val merged = mergeSettings(json("""{"statusLine":{"command":"theirs"}}"""), json("""{"statusLine":{"command":"ours"}}"""))

        assertEquals("ours", merged.getAsJsonObject("statusLine").get("command").asString)
    }

    @Test
    fun `the user's hooks keep running alongside ours`() {
        val merged = mergeSettings(
            json("""{"hooks":{"PreToolUse":[{"matcher":"theirs"}]}}"""),
            json("""{"hooks":{"PreToolUse":[{"matcher":"ours"}]}}""")
        )

        val rules = merged.getAsJsonObject("hooks").getAsJsonArray("PreToolUse")
        assertEquals(2, rules.size())
        assertEquals("theirs", rules[0].asJsonObject.get("matcher").asString)
        assertEquals("ours", rules[1].asJsonObject.get("matcher").asString)
    }

    @Test
    fun `a hook event only we use is added whole`() {
        val merged = mergeSettings(json("""{"hooks":{"PreToolUse":[{"matcher":"theirs"}]}}"""), json("""{"hooks":{"PreCompact":[{}]}}"""))

        val hooks = merged.getAsJsonObject("hooks")
        assertEquals(1, hooks.getAsJsonArray("PreToolUse").size())
        assertEquals(1, hooks.getAsJsonArray("PreCompact").size())
    }
}
