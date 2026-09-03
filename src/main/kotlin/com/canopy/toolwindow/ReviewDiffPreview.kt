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

class ReviewDiffPreview(tree: ChangesTree, owner: JComponent) :
    TreeHandlerEditorDiffPreview(tree, owner, ReviewedRows) {

    override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String =
        wrapper?.presentableName?.let { "Review: $it" } ?: "Review"

    /** The preview's own double-click handler runs before the browser's, so both have to be overridden. */
    override fun handleDoubleClick(event: MouseEvent): Boolean {
        val branch = tree.selectionPath?.takeIf { (it.lastPathComponent as? TreeNode)?.isLeaf == false }
            ?: return super.handleDoubleClick(event)

        if (tree.isExpanded(branch)) collapseSubtree(tree, branch) else expandSubtree(tree, branch)

        return true
    }

    override fun returnFocusToTree() = tree.requestFocus()
}

/** The platform's handler collects `Change` rows only, and an untracked row holds a [FilePath]. */
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

/** The platform keeps its own wrapper for this protected; the user object stays the tree's [FilePath]. */
private class UntrackedFileRow(private val path: FilePath) : ChangeViewDiffRequestProcessor.Wrapper() {

    private val asAddition = Change(null, CurrentContentRevision(path))

    override fun getUserObject(): Any = path

    override fun getPresentableName(): String = path.name

    override fun getFilePath(): FilePath = path

    override fun getFileStatus(): FileStatus = FileStatus.UNKNOWN

    override fun createProducer(project: Project?): DiffRequestProducer? =
        ChangeDiffRequestProducer.create(project, asAddition)
}
