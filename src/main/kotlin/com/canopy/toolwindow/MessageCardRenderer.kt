package com.canopy.toolwindow

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

/**
 * One message as a card: its number, its text wrapped to whatever width the panel has, and a strip
 * of thumbnails for anything pasted into it.
 *
 * The text wraps through HTML sized to the list, because a fixed character cut left half the panel
 * empty when the splitter was dragged wide and still truncated when it was narrow.
 */
class MessageCardRenderer : ListCellRenderer<SessionMessage> {

    private val card = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4)))
    private val ordinal = JLabel("", SwingConstants.RIGHT)
    private val text = JLabel()
    private val thumbnails = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))

    init {
        card.border = JBUI.Borders.empty(6, 8)
        card.isOpaque = true
        ordinal.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        ordinal.preferredSize = java.awt.Dimension(JBUI.scale(26), 0)
        thumbnails.isOpaque = false

        card.add(ordinal, BorderLayout.WEST)
        card.add(text, BorderLayout.CENTER)
        card.add(thumbnails, BorderLayout.SOUTH)
    }

    override fun getListCellRendererComponent(
        list: JList<out SessionMessage>,
        value: SessionMessage,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ): Component {
        val background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
        card.background = background
        text.foreground = if (selected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
        ordinal.foreground = if (selected) UIUtil.getListSelectionForeground(true) else JBColor.GRAY
        ordinal.text = value.ordinal.toString()
        text.text = wrappedHtml(value.text, textWidth(list))

        thumbnails.removeAll()
        for (image in value.images.take(MAX_THUMBNAILS)) {
            MessageThumbnails.of(image)?.let { thumbnails.add(JLabel(it)) }
        }
        thumbnails.isVisible = thumbnails.componentCount > 0

        return card
    }

    private fun textWidth(list: JList<out SessionMessage>): Int =
        (list.width - JBUI.scale(60)).coerceAtLeast(JBUI.scale(120))

    private companion object {
        const val MAX_THUMBNAILS = 4
    }
}

private const val PREVIEW_LINE_LIMIT = 3

/** Only the opening lines: the card is a way back into the conversation, not a reader for it. */
internal fun wrappedHtml(text: String, width: Int): String {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(PREVIEW_LINE_LIMIT).toList()
    val body = lines.joinToString("<br>") { escapeForHtml(it) }
    val more = if (text.lineSequence().count { it.isNotBlank() } > lines.size) "<br>…" else ""

    return "<html><body style='width:${width}px'>$body$more</body></html>"
}

private fun escapeForHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
