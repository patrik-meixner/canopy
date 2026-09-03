package com.canopy.model

sealed interface OrphanReason {

    data object NoSession : OrphanReason

    data object EmptyDirectory : OrphanReason

    data class LeftoverFiles(val names: List<String>) : OrphanReason

    data class MissingGitDir(val gitDir: String) : OrphanReason

    data class OwnedByOtherRepo(val commonDir: String) : OrphanReason
}
