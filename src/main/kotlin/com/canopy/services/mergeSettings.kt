package com.canopy.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject

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
