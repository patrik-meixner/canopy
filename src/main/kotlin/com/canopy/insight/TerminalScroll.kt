package com.canopy.insight

import com.intellij.openapi.application.ApplicationManager
import com.intellij.terminal.JBTerminalWidget
import java.awt.Container
import javax.swing.JScrollBar

object TerminalScroll {

    private const val KEY_LENGTH = 24

    fun scrollTo(widget: JBTerminalWidget, text: String): Boolean {
        val key = text.take(KEY_LENGTH).trim()
        if (key.isEmpty()) return false

        val buffer = widget.terminalTextBuffer
        val history: Int
        val lines: List<String>

        buffer.lock()
        try {
            history = buffer.historyLinesCount
            lines = (-history until buffer.screenLinesCount).map { buffer.getLine(it).getText() }
        } catch (_: Exception) {
            return false
        } finally {
            buffer.unlock()
        }

        val row = findScrollbackRow(lines, key)?.minus(history) ?: return false
        ApplicationManager.getApplication().invokeLater {
            scrollBarOf(widget)?.let { it.value = (row + history).coerceIn(it.minimum, it.maximum - it.visibleAmount) }
        }

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
