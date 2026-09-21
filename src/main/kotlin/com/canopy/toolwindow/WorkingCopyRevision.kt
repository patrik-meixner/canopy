package com.canopy.toolwindow

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import java.nio.file.Files
import java.nio.file.Path

/** The file on disk, read so that it compares against what git stored rather than against itself. */
class WorkingCopyRevision(
    private val root: String,
    private val relativePath: String,
    private val against: String,
    private val filePath: FilePath
) : ContentRevision {

    override fun getContent(): String? {
        val working = try {
            Files.readString(Path.of(root, relativePath))
        } catch (_: Exception) {
            return null
        }

        return comparableWorkingCopy(gitBlob(root, "$against:$relativePath"), working)
    }

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.Int(0)
}
