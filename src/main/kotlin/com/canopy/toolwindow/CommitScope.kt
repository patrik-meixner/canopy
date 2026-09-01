package com.canopy.toolwindow

/** What a commit is about: everything the session changed, or only the rows that were selected. */
sealed interface CommitScope {

    data object Everything : CommitScope

    data class Selection(val paths: List<String>) : CommitScope
}

/** Whether the commit is the end of it, or the first half of getting the work off the machine. */
enum class AfterCommit { Stay, Push }

/** Said in the panel itself, so nothing has to be remembered between clicking and typing. */
fun commitCaption(scope: CommitScope, repositories: Int): String {
    val where = if (repositories > 1) " across $repositories repositories" else ""

    if (scope is CommitScope.Selection) return "${scope.paths.size} selected file(s)$where"

    return "Everything this session changed$where"
}
