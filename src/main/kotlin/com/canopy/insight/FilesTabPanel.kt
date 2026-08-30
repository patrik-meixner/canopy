package com.canopy.insight

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * The files a session actually wrote to, grouped by the repository they live in.
 */
class FilesTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val root = DefaultMutableTreeNode()
    private val model = DefaultTreeModel(root)
    private val tree = Tree(model)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = FileRenderer()
        tree.emptyText.text = "This session has not written to any file yet"
        tree.border = JBUI.Borders.empty(4, 2)

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openSelected()
        }.installOn(tree)

        add(ScrollPaneFactory.createScrollPane(tree, true))
    }

    override fun render(insight: SessionInsight) {
        val expanded = (0 until root.childCount)
            .map { root.getChildAt(it) as DefaultMutableTreeNode }
            .filter { tree.isExpanded(TreePath(it.path)) }
            .map { it.userObject.toString() }
            .toSet()

        root.removeAllChildren()
        for ((repository, files) in groupByRepository(insight.files)) {
            val repositoryNode = DefaultMutableTreeNode(RepositoryRow(repository, files.size))
            files.forEach { repositoryNode.add(DefaultMutableTreeNode(it)) }
            root.add(repositoryNode)
        }
        model.reload()

        for (index in 0 until root.childCount) {
            val node = root.getChildAt(index) as DefaultMutableTreeNode
            if (expanded.isEmpty() || node.userObject.toString() in expanded) tree.expandPath(TreePath(node.path))
        }
    }

    private fun openSelected(): Boolean {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return false
        val file = node.userObject as? TouchedFile ?: return false
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(Path.of(file.path)) ?: return false

        return FileEditorManager.getInstance(project).openFile(virtualFile, true).isNotEmpty()
    }

    private data class RepositoryRow(val name: String, val count: Int) {
        override fun toString(): String = name
    }

    private class FileRenderer : ColoredTreeCellRenderer() {
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
                is RepositoryRow -> {
                    icon = AllIcons.Nodes.Module
                    append(node.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${node.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is TouchedFile -> {
                    icon = AllIcons.FileTypes.Any_type
                    append(Path.of(node.path).fileName.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("   ${Path.of(node.path).parent ?: ""}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    toolTipText = node.path
                }
            }
        }
    }
}

/** Files are grouped by their nearest enclosing directory that carries a .git, which is the repo. */
internal fun groupByRepository(files: List<TouchedFile>): Map<String, List<TouchedFile>> =
    files.groupBy { repositoryLabel(it.path) }.toSortedMap()

internal fun repositoryLabel(path: String): String {
    var current: Path? = Path.of(path).parent
    while (current != null) {
        if (java.nio.file.Files.exists(current.resolve(".git"))) return current.fileName?.toString() ?: current.toString()
        current = current.parent
    }

    return "Outside any repository"
}
