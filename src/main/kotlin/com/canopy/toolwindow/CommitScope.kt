package com.canopy.toolwindow

sealed interface CommitScope {

    data object Everything : CommitScope

    data class Selection(val paths: List<String>) : CommitScope
}

enum class AfterCommit { Stay, Push }

fun commitCaption(scope: CommitScope, repositories: Int): String {
    val where = if (repositories > 1) " across $repositories repositories" else ""

    if (scope is CommitScope.Selection) return "${scope.paths.size} selected file(s)$where"

    return "Everything this session changed$where"
}
