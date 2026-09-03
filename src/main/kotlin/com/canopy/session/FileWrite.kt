package com.canopy.session

sealed interface FileWrite {

    val path: String

    data class Whole(override val path: String, val content: String) : FileWrite

    data class Replace(override val path: String, val before: String, val after: String) : FileWrite
}
