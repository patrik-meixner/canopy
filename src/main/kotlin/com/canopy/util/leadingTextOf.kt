package com.canopy.util

import com.google.gson.JsonObject

/** The text a transcript message opens with, whether its content is a bare string or blocks. */
fun leadingTextOf(message: JsonObject?): String? {
    val content = message?.get("content") ?: return null
    if (content.isJsonPrimitive) return content.asString
    if (!content.isJsonArray) return null

    return content.asJsonArray
        .firstOrNull { it.isJsonObject && it.asJsonObject.get("type")?.asString == "text" }
        ?.asJsonObject?.get("text")?.asString
}
