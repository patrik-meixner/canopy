package com.canopy.insight

fun planCardKeys(tasks: List<PlannedTask>): List<String> =
    tasks.map { "${it.id}:${it.status}:${it.subject}:${it.description.length}" }
