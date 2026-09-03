package com.canopy.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * A turn opens at UserPromptSubmit and closes at Stop; nothing between them reopens it, so a tool
 * finishing is not the agent finishing. PreToolUse re-asserts it for a session resumed elsewhere.
 */
fun canopyHooks(notifyCommand: String, ledgerCommand: String): JsonObject {
    val entry = commands(notifyCommand)
    val ledger = commands(ledgerCommand)
    val unmatched = JsonArray().apply { add(JsonObject().apply { add("hooks", entry.deepCopy()) }) }
    val everyTool = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("matcher", ".*")
            add("hooks", entry.deepCopy())
        })
        add(rule(WRITING_TOOLS, ledger))
    }

    return JsonObject().apply {
        add("SessionStart", JsonArray().apply { add(JsonObject().apply { add("hooks", ledger.deepCopy()) }) })
        add("UserPromptSubmit", unmatched.deepCopy())
        add("PreToolUse", everyTool)
        add("PostToolUse", JsonArray().apply { add(rule(WRITING_TOOLS, ledger)) })
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

private fun commands(command: String): JsonArray = JsonArray().apply {
    add(JsonObject().apply {
        addProperty("type", "command")
        addProperty("command", command)
    })
}

private fun rule(matcher: String, hooks: JsonArray): JsonObject = JsonObject().apply {
    addProperty("matcher", matcher)
    add("hooks", hooks.deepCopy())
}

private const val WRITING_TOOLS = "^(Bash|Edit|Write|MultiEdit|NotebookEdit|mcp__.*)$"
