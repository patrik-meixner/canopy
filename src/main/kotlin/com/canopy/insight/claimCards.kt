package com.canopy.insight

/**
 * A key takes the card it already has and no one else may take it, so a key listed twice gets a
 * second card of its own rather than the same component in two places.
 */
fun <T> claimCards(keys: List<String>, held: Map<String, T>, build: (String) -> T): List<T> {
    val unclaimed = HashMap(held)

    return keys.map { key -> unclaimed.remove(key) ?: build(key) }
}
