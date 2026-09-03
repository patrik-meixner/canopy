package com.canopy.insight

import com.canopy.toolwindow.MessageThumbnails
import com.canopy.toolwindow.SessionMessage
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

private const val PREVIEW_LINES = 4

class MessageCard(
    private val message: SessionMessage,
    private val onOpen: (MessageCard, MouseEvent) -> Unit,
    private val onJump: () -> Unit
) : JPanel(BorderLayout()) {

    private val island = IslandPanel(BorderLayout(JBUI.scale(8), JBUI.scale(6)))

    init {
        isOpaque = false
        border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP, 0, InsightUi.GAP)
        island.border = JBUI.Borders.empty(8, 10)

        island.add(ordinal(), BorderLayout.WEST)
        island.add(text(), BorderLayout.CENTER)
        thumbnails()?.let { island.add(it, BorderLayout.SOUTH) }
        add(island, BorderLayout.CENTER)

        val opener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.isPopupTrigger || javax.swing.SwingUtilities.isRightMouseButton(event)) {
                    return menu().show(event.component, event.x, event.y)
                }

                onOpen(this@MessageCard, event)
            }

            override fun mousePressed(event: MouseEvent) {
                if (event.isPopupTrigger) menu().show(event.component, event.x, event.y)
            }

            override fun mouseEntered(event: MouseEvent) = highlight(true)

            override fun mouseExited(event: MouseEvent) = highlight(false)
        }
        addMouseListener(opener)
        island.addMouseListener(opener)
    }

    private fun menu() = javax.swing.JPopupMenu().apply {
        add(javax.swing.JMenuItem("Scroll Terminal to This Message").apply { addActionListener { onJump() } })
        add(javax.swing.JMenuItem("Copy Text").apply {
            addActionListener {
                com.intellij.openapi.ide.CopyPasteManager.getInstance()
                    .setContents(java.awt.datatransfer.StringSelection(message.text))
            }
        })
    }

    private fun highlight(hovered: Boolean) {
        island.islandColor = if (hovered) InsightUi.hoverBackground() else InsightUi.islandBackground()
        island.repaint()
    }

    private fun ordinal() = JLabel(message.ordinal.toString(), SwingConstants.RIGHT).apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = com.intellij.ui.JBColor.GRAY
        verticalAlignment = SwingConstants.TOP
        preferredSize = Dimension(JBUI.scale(22), 0)
    }

    private fun text() = JTextArea(previewOf(message.text)).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getLabelForeground()
        border = JBUI.Borders.empty()
        isFocusable = false
    }

    private fun thumbnails(): JPanel? {
        if (message.images.isEmpty()) return null

        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            message.images.forEach { image ->
                val label = JLabel(MessageThumbnails.cached(image)).apply {
                    preferredSize = Dimension(JBUI.scale(72), JBUI.scale(72))
                    toolTipText = "Click to enlarge"
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(event: MouseEvent) =
                            ImagePreview.show(this@apply, event.point, image)
                    })
                }
                add(label)
                if (label.icon != null) return@forEach

                whenFirstShown(label) {
                    MessageThumbnails.decodeInBackground(image) {
                        label.icon = it
                        label.revalidate()
                    }
                }
            }
        }
    }
}

internal fun previewOf(text: String): String {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val head = lines.take(PREVIEW_LINES).joinToString("\n")

    return if (lines.size > PREVIEW_LINES) "$head\n…" else head
}
