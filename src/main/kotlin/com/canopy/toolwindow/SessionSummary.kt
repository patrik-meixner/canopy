package com.canopy.toolwindow

import com.canopy.model.SessionDisplay

/**
 * What is outstanding, in the words a person would use to decide what to do next.
 *
 * The tree below says which files and in which state; this exists to answer whether anything needs
 * doing at all, without expanding four sections to find out.
 */
fun outstandingSummary(
    uncommitted: Int,
    untracked: Int,
    unpushed: Int,
    pushed: Int,
    repositories: Int
): String {
    if (uncommitted == 0 && unpushed == 0 && untracked == 0) {
        return if (pushed > 0) "Nothing outstanding, $pushed file(s) already pushed" else "Nothing outstanding"
    }

    val parts = mutableListOf<String>()
    if (uncommitted > 0) parts.add("$uncommitted uncommitted")
    if (untracked > 0) parts.add("$untracked untracked")
    if (unpushed > 0) parts.add("$unpushed to push")
    val where = if (repositories > 1) " across $repositories repositories" else ""

    return parts.joinToString(", ") + where
}

/** Who the session is and where it ran, so the header names what is being reviewed. */
fun sessionHeadline(session: SessionDisplay?, nowMillis: Long = System.currentTimeMillis()): String {
    if (session == null) return "No session selected"
    val place = placesFor(session)
    val age = relativeAge(session.modified.toEpochMilli(), nowMillis)

    return listOfNotNull(session.displayName.takeIf { it.isNotBlank() }, place, "${session.messageCount} messages", age)
        .joinToString("   ")
}
