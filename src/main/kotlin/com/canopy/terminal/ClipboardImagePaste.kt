package com.canopy.terminal

import com.intellij.terminal.JBTerminalPanel
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Routes Cmd+V to the CLI when the clipboard holds an image rather than text.
 *
 * The terminal's own paste only knows how to send text, so an image silently does nothing.
 * Claude Code reads the clipboard itself when it sees Ctrl+V, so forwarding that byte hands
 * the image over instead of dropping it.
 */
object ClipboardImagePaste {

    private const val CTRL_V = "\u0016"

    fun install(panel: JBTerminalPanel, sendToTerminal: (String) -> Unit) {
        panel.addPreKeyEventHandler { event ->
            if (!isImagePasteShortcut(event)) return@addPreKeyEventHandler

            event.consume()
            sendToTerminal(CTRL_V)
        }
    }

    private fun isImagePasteShortcut(event: KeyEvent): Boolean {
        if (event.id != KeyEvent.KEY_PRESSED) return false
        if (event.keyCode != KeyEvent.VK_V) return false
        if (event.modifiersEx and InputEvent.META_DOWN_MASK == 0) return false

        return clipboardHoldsImage()
    }

    private fun clipboardHoldsImage(): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) ||
                clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)
        } catch (_: Exception) {
            false
        }
    }
}
