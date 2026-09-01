package com.canopy.toolwindow

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.containers.JBIterable
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.tree.TreeNode

/**
 * The Commit window's diff, opened from the review tree.
 *
 * Opening a diff per file filled the tab strip with a tab per file clicked, and each was a dead end:
 * no way through to the next change without going back to the tree. This is the platform's own
 * preview, so it is one tab whose content is swapped, and it arrives with the toolbar that walks
 * the selection.
 */
class ReviewDiffPreview(tree: ChangesTree, owner: JComponent) :
    TreeHandlerEditorDiffPreview(tree, owner, ReviewedRows) {

    override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String =
        wrapper?.presentableName?.let { "Review: $it" } ?: "Review"

    /**
     * A directory opens, it does not diff.
     *
     * The preview installs its own double-click handler on the tree, and it runs before the
     * browser's, so overriding the browser alone left the directory diffing as it always had.
     */
    override fun handleDoubleClick(event: MouseEvent): Boolean {
        val branch = tree.selectionPath?.takeIf { (it.lastPathComponent as? TreeNode)?.isLeaf == false }
            ?: return super.handleDoubleClick(event)

        expandSubtree(tree, branch)

        return true
    }

    override fun returnFocusToTree() = tree.requestFocus()
}

/**
 * Every row of the review tree that stands for a file, tracked or not.
 *
 * The platform's own handler collects `Change` rows only. An untracked row holds a [FilePath], so
 * selecting one produced an empty selection, the preview fell back to every file in the tree, and
 * clicking a file opened whichever one happened to be first. The class that would have made this
 * three lines is marked internal, so the three methods are written out.
 */
private object ReviewedRows : ChangesTreeDiffPreviewHandler() {

    override fun iterateSelectedChanges(tree: ChangesTree): JBIterable<ChangeViewDiffRequestProcessor.Wrapper> =
        rowsOf(VcsTreeModelData.selected(tree))

    override fun iterateAllChanges(tree: ChangesTree): JBIterable<ChangeViewDiffRequestProcessor.Wrapper> =
        rowsOf(VcsTreeModelData.all(tree))

    override fun selectChange(tree: ChangesTree, change: ChangeViewDiffRequestProcessor.Wrapper) =
        tree.setSelectedChanges(listOf(change.userObject))

    private fun rowsOf(data: VcsTreeModelData) = data.iterateUserObjects().filterMap(::wrapperFor)

    private fun wrapperFor(userObject: Any): ChangeViewDiffRequestProcessor.Wrapper? = when (userObject) {
        is Change -> ChangeViewDiffRequestProcessor.ChangeWrapper(userObject)
        is FilePath -> UntrackedFileRow(userObject)
        else -> null
    }
}

/**
 * An untracked row, as something the diff viewer can walk to.
 *
 * The platform has a wrapper for exactly this and keeps it protected, so this is that shape: a file
 * that exists on disk and nowhere in git, which diffs as an addition. Its user object stays the
 * [FilePath] the tree holds, so walking the selection keeps the tree in step.
 */
private class UntrackedFileRow(private val path: FilePath) : ChangeViewDiffRequestProcessor.Wrapper() {

    private val asAddition = Change(null, CurrentContentRevision(path))

    override fun getUserObject(): Any = path

    override fun getPresentableName(): String = path.name

    override fun getFilePath(): FilePath = path

    override fun getFileStatus(): FileStatus = FileStatus.UNKNOWN

    override fun createProducer(project: Project?): DiffRequestProducer? =
        ChangeDiffRequestProducer.create(project, asAddition)
}
