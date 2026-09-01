package com.canopy.editor

import com.canopy.toolwindow.GlyphIcon
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.RowIcon
import javax.swing.Icon

class ClaudeSessionIconProvider : FileIconProvider {

    /**
     * State lives in a fixed-size badge beside the agent's mark rather than in the title text: a
     * glyph has a variable advance width, so putting it in the text resized the tab and reflowed
     * the whole strip every time the state changed. The badge is always there, idle or not.
     */
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file !is ClaudeSessionVirtualFile) return null

        val badge = GlyphIcon({ project?.let { file.statusGlyph(it) }.orEmpty() })
        val agent = if (file.isShellSession) SHELL_ICON else CLAUDE_ICON

        return if (file.isWorktreeSession) RowIcon(agent, TREE_ICON, badge) else RowIcon(agent, badge)
    }

    companion object {
        /** A session tab runs claude; a shell tab runs whatever the user typed, so it says terminal instead. */
        private val CLAUDE_ICON = IconLoader.getIcon("/icons/claude.svg", ClaudeSessionIconProvider::class.java)
        private val SHELL_ICON: Icon = com.intellij.icons.AllIcons.Debugger.Console
        val TREE_ICON: Icon = IconLoader.getIcon("/icons/worktree.svg", ClaudeSessionIconProvider::class.java)
    }
}
