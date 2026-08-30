package com.canopy.toolwindow

import com.canopy.util.ProcessHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import java.nio.file.Files
import java.nio.file.Path

/**
 * File content fetched when the diff is opened rather than when the tree is built.
 *
 * Reading every revision up front costs two git processes per changed file, which is what made a
 * few hundred changed files take seconds before anything appeared.
 */
class GitContentRevision(
    private val root: String,
    private val relativePath: String,
    private val revision: String?,
    private val filePath: FilePath
) : ContentRevision {

    override fun getContent(): String? =
        if (revision == null) workingCopy() else committed()

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber =
        VcsRevisionNumber.Int(0).takeIf { revision == null } ?: TextRevisionNumber(revision!!)

    private fun workingCopy(): String? = try {
        Files.readString(Path.of(root, relativePath))
    } catch (_: Exception) {
        null
    }

    private fun committed(): String? = try {
        val result = ProcessHelper.execWithTimeout(
            command = arrayOf("git", "-C", root, "show", "$revision:$relativePath"),
            timeoutMs = 20_000,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        if (result.exitCode == 0) result.output else null
    } catch (_: Exception) {
        null
    }
}

private class TextRevisionNumber(private val revision: String) : VcsRevisionNumber {
    override fun asString(): String = revision
    override fun compareTo(other: VcsRevisionNumber): Int = asString().compareTo(other.asString())
}
