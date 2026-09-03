package com.canopy.session

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

fun writesAccountFor(head: String, current: String, writes: List<FileWrite>): Boolean =
    writes.isNotEmpty() && replayWrites(head, writes) == current
