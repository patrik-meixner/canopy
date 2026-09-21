package com.canopy.settings

private const val DEFAULT_DIRECTORY = ".claude"
private const val SUFFIX_PREFIX = "$DEFAULT_DIRECTORY-"

fun discoverProfiles(homeEntries: List<String>): List<ClaudeProfile> {
    val default = homeEntries.filter { it == DEFAULT_DIRECTORY }.map { ClaudeProfile("Default", it) }
    val named = homeEntries
        .filter { it.startsWith(SUFFIX_PREFIX) && it.length > SUFFIX_PREFIX.length }
        .sorted()
        .map { ClaudeProfile(nameOf(it.removePrefix(SUFFIX_PREFIX)), it) }

    return default + named
}

private fun nameOf(suffix: String) = suffix.replace('-', ' ').replaceFirstChar { it.uppercase() }
