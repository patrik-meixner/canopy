package com.canopy.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Canopy's own settings laid over the user's, rather than in place of them.
 *
 * `--settings` replaces a settings file rather than adding to one, so handing Claude Code a file
 * holding only a status line and some hooks silently dropped everything else the user had
 * configured. Hook arrays are appended to so the user's own hooks still run alongside ours.
 */
fun mergeSettings(base: JsonObject, ours: JsonObject): JsonObject {
    val merged = base.deepCopy()

    ours.entrySet().forEach { (key, value) ->
        val existing = merged.get(key)
        if (key != "hooks" || existing == null || !existing.isJsonObject || !value.isJsonObject) {
            return@forEach merged.add(key, value.deepCopy())
        }

        merged.add(key, mergedHooks(existing.asJsonObject, value.asJsonObject))
    }

    return merged
}

private fun mergedHooks(base: JsonObject, ours: JsonObject): JsonObject {
    val merged = base.deepCopy()

    ours.entrySet().forEach { (event, rules) ->
        val existing = merged.get(event)
        if (existing == null || !existing.isJsonArray || !rules.isJsonArray) {
            return@forEach merged.add(event, rules.deepCopy())
        }

        val combined = JsonArray()
        existing.asJsonArray.forEach { combined.add(it.deepCopy()) }
        rules.asJsonArray.forEach { combined.add(it.deepCopy()) }
        merged.add(event, combined)
    }

    return merged
}
