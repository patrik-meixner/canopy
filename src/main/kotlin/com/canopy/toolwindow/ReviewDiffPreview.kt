package com.canopy.toolwindow

import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.DefaultChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import javax.swing.JComponent

/**
 * The Commit window's diff, opened from the review tree.
 *
 * Opening a diff per file filled the tab strip with a tab per file clicked, and each was a dead end:
 * no way through to the next change without going back to the tree. This is the platform's own
 * preview, so it is one tab whose content is swapped, and it arrives with the toolbar that walks
 * the selection.
 */
class ReviewDiffPreview(tree: ChangesTree, owner: JComponent) :
    TreeHandlerEditorDiffPreview(tree, owner, DefaultChangesTreeDiffPreviewHandler) {

    override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String =
        wrapper?.presentableName?.let { "Review: $it" } ?: "Review"

    override fun returnFocusToTree() = tree.requestFocus()
}
