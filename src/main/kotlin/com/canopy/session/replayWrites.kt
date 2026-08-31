package com.canopy.session

/**
 * What a file would hold if these writes, and only these, had been applied to [from].
 *
 * Null when the writes cannot account for the file: a replacement whose target is not there means
 * something else has been editing, and no answer is better than a wrong one.
 */
fun replayWrites(from: String, writes: List<FileWrite>): String? {
    var content = from

    for (write in writes) {
        content = when (write) {
            is FileWrite.Whole -> write.content
            is FileWrite.Replace -> {
                if (!content.contains(write.before)) return null

                content.replaceFirst(write.before, write.after)
            }
        }
    }

    return content
}

/** Whether [current] is exactly what those writes would have produced from [head]. */
fun writesAccountFor(head: String, current: String, writes: List<FileWrite>): Boolean =
    writes.isNotEmpty() && replayWrites(head, writes) == current
