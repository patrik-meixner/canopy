package com.canopy.insight

import com.intellij.openapi.application.ApplicationManager
import com.intellij.terminal.JBTerminalWidget
import java.awt.Container
import javax.swing.JScrollBar

/**
 * Finds a line in a terminal's scrollback and scrolls to it.
 */
object TerminalScroll {

    private const val KEY_LENGTH = 40

    fun scrollTo(widget: JBTerminalWidget, text: String): Boolean {
        val key = text.take(KEY_LENGTH).trim()
        if (key.isEmpty()) return false

        val buffer = widget.terminalTextBuffer
        var target: Int? = null
        var history = 0

        buffer.lock()
        try {
            history = buffer.historyLinesCount
            for (row in -history until buffer.screenLinesCount) {
                if (buffer.getLine(row).getText().contains(key)) {
                    target = row
                    break
                }
            }
        } catch (_: Exception) {
            return false
        } finally {
            buffer.unlock()
        }

        val row = target ?: return false
        val lines = history
        ApplicationManager.getApplication().invokeLater { scrollBarOf(widget)?.let { it.value = (row + lines).coerceIn(it.minimum, it.maximum - it.visibleAmount) } }

        return true
    }

    private fun scrollBarOf(container: Container): JScrollBar? {
        for (component in container.components) {
            if (component is JScrollBar && component.orientation == JScrollBar.VERTICAL) return component
            if (component is Container) scrollBarOf(component)?.let { return it }
        }

        return null
    }
}
