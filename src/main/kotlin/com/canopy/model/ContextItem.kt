package com.canopy.model

import java.nio.file.Path

enum class ContextKind(val label: String) {
    RULE("Rules"),
    AGENT("Agents"),
    SKILL("Skills"),
    MEMORY("Memory")
}

/**
 * Where context comes from. Claude Code merges three of these, and which one a file sits in decides
 * whether it applies to everything you do, to this repository, or only to this session's checkout.
 */
enum class ContextLevel(val label: String) {
    PERSONAL("Personal"),
    PROJECT("Project"),
    SESSION("Session")
}

data class ContextSource(
    val level: ContextLevel,
    val kind: ContextKind,
    val directory: Path
)

data class ContextItem(
    val name: String,
    val description: String?,
    val source: ContextSource,
    val path: Path
) {
    /** Text that invokes this item from a prompt; rules and memory are loaded, never called. */
    val insertText: String
        get() = when (source.kind) {
            ContextKind.SKILL -> "/$name "
            ContextKind.AGENT -> "@$name "
            ContextKind.RULE, ContextKind.MEMORY -> ""
        }
}

data class ContextGroup(
    val source: ContextSource,
    val items: List<ContextItem>
)
