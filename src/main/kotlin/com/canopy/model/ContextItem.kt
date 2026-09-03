package com.canopy.model

import java.nio.file.Path

enum class ContextKind(val label: String) {
    RULE("Rules"),
    AGENT("Agents"),
    SKILL("Skills"),
    MEMORY("Memory")
}

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
