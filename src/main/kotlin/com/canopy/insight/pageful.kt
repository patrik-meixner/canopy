package com.canopy.insight

data class Pageful<T>(val shown: List<T>, val remaining: Int)

fun <T> pageful(items: List<T>, limit: Int): Pageful<T> {
    if (items.size <= limit) return Pageful(items, 0)

    return Pageful(items.take(limit), items.size - limit)
}
