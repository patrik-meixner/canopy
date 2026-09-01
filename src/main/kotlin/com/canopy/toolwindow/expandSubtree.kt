package com.canopy.toolwindow

import javax.swing.JTree
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * Everything under one row, rather than everything.
 *
 * The platform expands a whole tree or one level of it, and neither answers "show me this module":
 * Expand All opens four hundred pushed files to reach one directory.
 */
fun expandSubtree(tree: JTree, path: TreePath) {
    val node = path.lastPathComponent as? TreeNode ?: return

    tree.expandPath(path)
    for (index in 0 until node.childCount) {
        expandSubtree(tree, path.pathByAddingChild(node.getChildAt(index)))
    }
}

/** Children first: collapsing the parent alone leaves its descendants open underneath it. */
fun collapseSubtree(tree: JTree, path: TreePath) {
    val node = path.lastPathComponent as? TreeNode ?: return

    for (index in 0 until node.childCount) {
        collapseSubtree(tree, path.pathByAddingChild(node.getChildAt(index)))
    }
    tree.collapsePath(path)
}
