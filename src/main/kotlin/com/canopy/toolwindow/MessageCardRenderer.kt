package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.canopy.insight.IslandPanel
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

class MessageCardRenderer : ListCellRenderer<SessionMessage> {

    private val outer = JPanel(BorderLayout())
    private val card = IslandPanel(BorderLayout(JBUI.scale(8), JBUI.scale(6)))
    private val ordinal = JLabel("", SwingConstants.RIGHT)
    private val text = JLabel()
    private val thumbnails = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))

    init {
        outer.isOpaque = true
        outer.border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP, 0, InsightUi.GAP)
        card.border = JBUI.Borders.empty(8, 10)
        ordinal.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        ordinal.preferredSize = java.awt.Dimension(JBUI.scale(24), 0)
        ordinal.verticalAlignment = SwingConstants.TOP
        text.verticalAlignment = SwingConstants.TOP
        thumbnails.isOpaque = false

        card.add(ordinal, BorderLayout.WEST)
        card.add(text, BorderLayout.CENTER)
        card.add(thumbnails, BorderLayout.SOUTH)
        outer.add(card, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out SessionMessage>,
        value: SessionMessage,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ): Component {
        outer.background = UIUtil.getListBackground()
        card.islandColor = InsightUi.cardBackground(selected)
        text.foreground = InsightUi.cardForeground(selected)
        ordinal.foreground = InsightUi.mutedForeground(selected)
        ordinal.text = value.ordinal.toString()
        text.text = wrappedHtml(value.text, textWidth(list))

        thumbnails.removeAll()
        for (image in value.images.take(MAX_THUMBNAILS)) {
            val ready = MessageThumbnails.cached(image)
            if (ready != null) {
                thumbnails.add(JLabel(ready))
                continue
            }

            MessageThumbnails.decodeInBackground(image) { list.repaint() }
        }
        thumbnails.isVisible = thumbnails.componentCount > 0

        return outer
    }

    private fun textWidth(list: JList<out SessionMessage>): Int =
        (list.width - JBUI.scale(72)).coerceAtLeast(JBUI.scale(120))

    private companion object {
        const val MAX_THUMBNAILS = 4
    }
}

private const val PREVIEW_LINE_LIMIT = 3

internal fun wrappedHtml(text: String, width: Int): String {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(PREVIEW_LINE_LIMIT).toList()
    val body = lines.joinToString("<br>") { escapeForHtml(it) }
    val more = if (text.lineSequence().count { it.isNotBlank() } > lines.size) "<br>…" else ""

    return "<html><body style='width:${width}px'>$body$more</body></html>"
}

private fun escapeForHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
