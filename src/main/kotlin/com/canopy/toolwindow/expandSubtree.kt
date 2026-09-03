package com.canopy.toolwindow

import javax.swing.JTree
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

fun expandSubtree(tree: JTree, path: TreePath) {
    val node = path.lastPathComponent as? TreeNode ?: return

    tree.expandPath(path)
    for (index in 0 until node.childCount) {
        expandSubtree(tree, path.pathByAddingChild(node.getChildAt(index)))
    }
}

fun collapseSubtree(tree: JTree, path: TreePath) {
    val node = path.lastPathComponent as? TreeNode ?: return

    for (index in 0 until node.childCount) {
        collapseSubtree(tree, path.pathByAddingChild(node.getChildAt(index)))
    }
    tree.collapsePath(path)
}
