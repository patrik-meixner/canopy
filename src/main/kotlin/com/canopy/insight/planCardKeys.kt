package com.canopy.insight

/**
 * What makes one drawn task list different from another.
 *
 * The card list leaves an unchanged list alone, which is what stops it jumping under the pointer.
 * A key that named only the task made a list whose tasks had all moved on look unchanged, so the
 * cards kept saying Pending under a summary that counted them done.
 */
fun planCardKeys(tasks: List<PlannedTask>): List<String> =
    tasks.map { "${it.id}:${it.status}:${it.subject}:${it.description.length}" }
