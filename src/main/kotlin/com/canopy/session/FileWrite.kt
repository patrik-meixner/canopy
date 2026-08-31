package com.canopy.session

/**
 * A write an agent made to one file, in the only two shapes it can take.
 *
 * A whole-file write carries its content; a replacement carries what it looked for and what it put
 * there. Keeping them apart means a write with no content, or a replacement with no target, is not
 * something the type system will let anyone construct.
 */
sealed interface FileWrite {

    val path: String

    data class Whole(override val path: String, val content: String) : FileWrite

    data class Replace(override val path: String, val before: String, val after: String) : FileWrite
}
