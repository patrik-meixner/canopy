package com.canopy.toolwindow

import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes

/**
 * A commit as a folder of the files it changed.
 *
 * Grouping pushed work by directory says where it landed; grouping it by commit says what it was.
 */
class CommitBrowserNode(private val entry: CommitWithChanges) : ChangesBrowserNode<CommitWithChanges>(entry) {

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
        renderer.append(entry.commit.subject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        renderer.append("   ${entry.commit.sha.take(7)}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        renderer.append("  ${relativeAge(entry.commit.atMillis, System.currentTimeMillis())}",
            SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        appendCount(renderer)
        renderer.icon = com.intellij.icons.AllIcons.Vcs.CommitNode
    }

    override fun getTextPresentation(): String = entry.commit.subject
}
