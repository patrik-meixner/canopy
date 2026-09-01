package com.canopy.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The hooks Canopy needs to know what a session is doing, scoped to a turn.
 *
 * The unit is the turn, not the tool call. Keying on tools meant a turn that ran six of them
 * reported working, idle, working, idle, working, idle: `PostToolUse` fired between each, and a
 * tool finishing says nothing about whether the agent is done. A turn opens at
 * [UserPromptSubmit] and closes at [Stop], and nothing in between reopens or closes it.
 *
 * `PreToolUse` stays because it re-asserts the turn: a session resumed from outside Canopy, or one
 * that has just had a permission prompt answered, is working again without a prompt being
 * submitted here.
 */
fun canopyHooks(notifyCommand: String): JsonObject {
    val entry = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("type", "command")
            addProperty("command", notifyCommand)
        })
    }
    val unmatched = JsonArray().apply { add(JsonObject().apply { add("hooks", entry.deepCopy()) }) }
    val everyTool = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("matcher", ".*")
            add("hooks", entry.deepCopy())
        })
    }

    return JsonObject().apply {
        add("UserPromptSubmit", unmatched.deepCopy())
        add("PreToolUse", everyTool)
        add("Stop", unmatched.deepCopy())
        add("SessionEnd", unmatched.deepCopy())
        add("PreCompact", unmatched.deepCopy())
        add("PostCompact", unmatched.deepCopy())
        add("Notification", JsonArray().apply {
            for (type in listOf("idle_prompt", "permission_prompt")) {
                add(JsonObject().apply {
                    addProperty("matcher", type)
                    add("hooks", entry.deepCopy())
                })
            }
        })
    }
}
