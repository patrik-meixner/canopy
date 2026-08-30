package com.canopy.util

import com.canopy.model.RepoScope

/**
 * Which repos the written working trees belong to, most-touched first.
 *
 * Submodules sit inside the superproject, so the longest matching root wins; matching the
 * superproject first would attribute every submodule edit to it.
 */
fun repoLabelsFor(roots: Map<String, Int>, scopes: List<RepoScope>): List<String> {
    if (roots.isEmpty() || scopes.isEmpty()) return emptyList()

    val byDepth = scopes.sortedByDescending { it.root.length }

    return roots.entries
        .mapNotNull { (root, count) -> byDepth.firstOrNull { root.startsWith(it.root) }?.label?.to(count) }
        .groupingBy { it.first }
        .fold(0) { total, (_, count) -> total + count }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
}
