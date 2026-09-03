package com.canopy.toolwindow

fun <T> unionByPath(items: List<T>, pathOf: (T) -> String?): List<T> {
    val seen = LinkedHashMap<String, T>()

    items.forEach { item ->
        val path = pathOf(item) ?: return@forEach

        seen.putIfAbsent(path, item)
    }

    return seen.values.toList()
}
