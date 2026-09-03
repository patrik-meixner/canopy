package com.canopy.editor

import com.canopy.toolwindow.GlyphIcon
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.RowIcon
import javax.swing.Icon

class ClaudeSessionIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file !is ClaudeSessionVirtualFile) return null

        val badge = GlyphIcon({ project?.let { file.statusGlyph(it) }.orEmpty() })
        val agent = if (file.isShellSession) SHELL_ICON else CLAUDE_ICON

        return if (file.isWorktreeSession) RowIcon(agent, TREE_ICON, badge) else RowIcon(agent, badge)
    }

    companion object {
        private val CLAUDE_ICON = IconLoader.getIcon("/icons/claude.svg", ClaudeSessionIconProvider::class.java)
        private val SHELL_ICON: Icon = com.intellij.icons.AllIcons.Debugger.Console
        val TREE_ICON: Icon = IconLoader.getIcon("/icons/worktree.svg", ClaudeSessionIconProvider::class.java)
    }
}
