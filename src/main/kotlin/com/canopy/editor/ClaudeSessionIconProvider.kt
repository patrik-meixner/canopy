package com.canopy.editor

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.RowIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

class ClaudeSessionIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file !is ClaudeSessionVirtualFile) return null
        // Status lives in a fixed-size badge appended to the tab icon (never in the title
        // text) so the tab keeps a constant width as Claude's state cycles. The badge box
        // is always present — idle paints nothing — so even the badge column never resizes.
        val badge = badgeFor(project?.let { file.statusGlyph(it) })
        val agent = if (file.isShellSession) SHELL_ICON else CLAUDE_ICON

        return if (file.isWorktreeSession) RowIcon(agent, TREE_ICON, badge) else RowIcon(agent, badge)
    }

    companion object {
        /** A session tab runs claude; a shell tab runs whatever the user typed, so it says terminal instead. */
        private val CLAUDE_ICON = IconLoader.getIcon("/icons/claude.svg", ClaudeSessionIconProvider::class.java)
        private val SHELL_ICON: Icon = com.intellij.icons.AllIcons.Debugger.Console
        val TREE_ICON: Icon = IconLoader.getIcon("/icons/worktree.svg", ClaudeSessionIconProvider::class.java)

        // One shared icon instance per glyph (colors are read from the component at paint
        // time, so instances are safe to reuse across tabs). Empty string = idle placeholder.
        private val badgeCache = HashMap<String, StatusGlyphIcon>()

        /** Matches the weight the same glyph has in the session list, so the two read as one thing. */
        private const val GLYPH_TO_BOX = 0.9f

        @Synchronized
        private fun badgeFor(glyph: String?): StatusGlyphIcon =
            badgeCache.getOrPut(glyph ?: "") { StatusGlyphIcon(glyph ?: "") }
    }

    /** Paints a status glyph centered in a fixed 16px box; an empty glyph reserves the box but draws nothing. */
    private class StatusGlyphIcon(private val glyph: String) : Icon {
        private val size = JBUIScale.scale(16)
        private val spins = com.canopy.toolwindow.isSpinnerFrame(glyph)

        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            if (glyph.isEmpty()) return
            // A working session spins in the tab as it does in the list. Asking for the next repaint
            // from the paint itself keeps it to the tabs that are on screen and showing a spinner.
            val drawn = if (spins) com.canopy.toolwindow.spinnerFrame(System.currentTimeMillis()) else glyph
            if (spins) c?.repaint(com.canopy.toolwindow.SPINNER_FRAME_MS)

            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.color = c?.foreground ?: UIUtil.getLabelForeground()
                g2.font = (c?.font ?: UIUtil.getLabelFont()).deriveFont(size * GLYPH_TO_BOX)
                val fm = g2.fontMetrics
                val tx = x + (size - fm.stringWidth(drawn)) / 2f
                val ty = y + (size - fm.height) / 2f + fm.ascent
                g2.drawString(drawn, tx, ty)
            } finally {
                g2.dispose()
            }
        }
    }
}
