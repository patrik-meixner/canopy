package com.canopy.insight

data class Pageful<T>(val shown: List<T>, val remaining: Int)

/**
 * The first [limit] of a list, and how many are still behind it.
 *
 * Every list here can run to thousands: a session's messages, its commits, months of sessions.
 * Rendering all of them costs the same whether or not anyone scrolls that far.
 */
fun <T> pageful(items: List<T>, limit: Int): Pageful<T> {
    if (items.size <= limit) return Pageful(items, 0)

    return Pageful(items.take(limit), items.size - limit)
}
