package com.canopy.insight

import com.canopy.toolwindow.MessageThumbnails
import com.canopy.toolwindow.SessionMessage
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * The whole message, on demand.
 *
 * A row that grows to fit its longest message makes the list unreadable, and one that clips loses
 * exactly the part worth reading. The row stays a fixed preview and the full text opens beside it.
 */
object MessagePreview {

    private var open: JBPopup? = null

    fun show(over: Component, at: Point, message: SessionMessage, onJump: (() -> Unit)?) {
        hide()

        val content = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(10)
            add(textArea(message.text), BorderLayout.CENTER)
            imageStrip(message)?.let { add(it, BorderLayout.SOUTH) }
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, null)
            .setRequestFocus(false)
            .setResizable(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setMinSize(Dimension(JBUI.scale(420), JBUI.scale(160)))
            .createPopup()

        open = popup
        popup.show(com.intellij.ui.awt.RelativePoint(over, at))
        onJump?.invoke()
    }

    fun hide() {
        open?.cancel()
        open = null
    }

    private fun textArea(text: String): Component {
        val area = JTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.empty()
            font = UIUtil.getLabelFont()
        }

        return ScrollPaneFactory.createScrollPane(area, true).apply {
            preferredSize = Dimension(JBUI.scale(460), JBUI.scale(220))
        }
    }

    private fun imageStrip(message: SessionMessage): Component? {
        if (message.images.isEmpty()) return null

        val strip = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply { isOpaque = false }
        message.images.forEach { image ->
            MessageThumbnails.full(image)?.let { strip.add(JBLabel(it)) }
        }

        return ScrollPaneFactory.createScrollPane(strip, true).apply {
            preferredSize = Dimension(JBUI.scale(460), JBUI.scale(240))
        }
    }
}

/** A single image, as large as a popup can sensibly hold. */
object ImagePreview {

    private var open: JBPopup? = null

    fun show(over: Component, at: Point, base64: String) {
        val icon = MessageThumbnails.full(base64) ?: return
        hide()

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6)
            add(JBLabel(icon))
        }

        open = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(false)
            .setCancelOnClickOutside(true)
            .createPopup()
            .also { it.show(com.intellij.ui.awt.RelativePoint(over, at)) }
    }

    fun hide() {
        open?.cancel()
        open = null
    }
}
