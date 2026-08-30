package com.canopy.toolwindow

import com.canopy.model.RepoScope
import com.canopy.insight.InsightUi
import com.canopy.services.RepoScopeService
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Local branches per repository, including each submodule's own.
 *
 * A branch already checked out in a worktree cannot be checked out again, so the row says which
 * worktree holds it rather than letting the attempt fail.
 */
class BranchTreePanel(
    private val project: Project,
    parent: Disposable,
    private val onNewWorktreeFromBranch: (RepoScope, String) -> Unit
) : JPanel(BorderLayout()), Disposable {

    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    private val tree = Tree(model)
    private val inFlight = AtomicBoolean(false)

    init {
        Disposer.register(parent, this)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = BranchTreeCellRenderer()
        tree.emptyText.text = "No branches found"
        tree.border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP)

        PopupHandler.installPopupMenu(tree, contextMenu(), "CanopyBranchTree")
        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
    }

    fun refresh() {
        if (!inFlight.compareAndSet(false, true)) return

        CanopyExecutor.submit {
            try {
                val scopes = RepoScopeService.getInstance(project).cachedScopes()
                val listed = scopes.map { it to BranchInventory.list(it.root, WorktreeInspector.list(it.root)) }

                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater

                    rebuild(listed)
                }
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun rebuild(listed: List<Pair<RepoScope, List<BranchEntry>>>) {
        val expanded = (0 until root.childCount)
            .map { root.getChildAt(it) as DefaultMutableTreeNode }
            .filter { tree.isExpanded(TreePath(it.path)) }
            .map { it.userObject.toString() }
            .toSet()

        root.removeAllChildren()
        for ((scope, branches) in listed) {
            if (branches.isEmpty()) continue
            val repoNode = DefaultMutableTreeNode(RepoRow(scope, branches.size))
            branches.forEach { repoNode.add(DefaultMutableTreeNode(BranchRow(scope, it))) }
            root.add(repoNode)
        }
        model.reload()

        for (index in 0 until root.childCount) {
            val node = root.getChildAt(index) as DefaultMutableTreeNode
            if (expanded.isEmpty() || node.userObject.toString() in expanded) tree.expandPath(TreePath(node.path))
        }
    }

    private fun contextMenu() = DefaultActionGroup().apply {
        add(object : AnAction("New Worktree from Branch", "Check this branch out in its own worktree", AllIcons.General.Add) {
            override fun actionPerformed(event: AnActionEvent) {
                val row = selectedBranch() ?: return

                onNewWorktreeFromBranch(row.scope, row.branch.name)
            }

            override fun update(event: AnActionEvent) {
                val row = selectedBranch()
                event.presentation.isEnabled = row != null && row.branch.worktreePath == null
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
        add(object : AnAction("Copy Branch Name", "Copy this branch's name", AllIcons.Actions.Copy) {
            override fun actionPerformed(event: AnActionEvent) {
                val name = selectedBranch()?.branch?.name ?: return

                com.intellij.openapi.ide.CopyPasteManager.getInstance()
                    .setContents(java.awt.datatransfer.StringSelection(name))
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = selectedBranch() != null
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })
    }

    private fun selectedBranch(): BranchRow? =
        ((tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? BranchRow)

    override fun dispose() = Unit

    private data class RepoRow(val scope: RepoScope, val count: Int) {
        override fun toString(): String = scope.label
    }

    private data class BranchRow(val scope: RepoScope, val branch: BranchEntry)

    private class BranchTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            when (val node = (value as? DefaultMutableTreeNode)?.userObject) {
                is RepoRow -> {
                    icon = if (node.scope.isSubmodule) AllIcons.Nodes.Module else AllIcons.Nodes.ModuleGroup
                    append(node.scope.label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${node.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is BranchRow -> renderBranch(node.branch)
            }
        }

        private fun renderBranch(branch: BranchEntry) {
            icon = AllIcons.Vcs.Branch
            append(branch.name, if (branch.isCurrent) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                else SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("   ${branchNote(branch)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            toolTipText = branch.worktreePath
        }
    }
}

/** What decides the next action: where it is checked out, how far it has drifted, whether it is dead. */
internal fun branchNote(branch: BranchEntry): String {
    val parts = mutableListOf<String>()
    branch.worktreePath?.let { parts.add("in " + it.substringAfterLast('/')) }
    if (branch.ahead > 0) parts.add("↑${branch.ahead}")
    if (branch.behind > 0) parts.add("↓${branch.behind}")
    if (branch.isGone) parts.add("remote gone")
    if (branch.upstream == null && !branch.isGone) parts.add("never pushed")
    parts.add(relativeAge(branch.committedAtMillis, System.currentTimeMillis()))

    return parts.joinToString("  ")
}
