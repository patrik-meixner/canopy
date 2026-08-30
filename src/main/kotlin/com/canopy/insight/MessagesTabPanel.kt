package com.canopy.insight

import com.canopy.editor.ClaudeSessionEditor
import com.canopy.toolwindow.MessageCardRenderer
import com.canopy.toolwindow.SessionMessage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * What you asked this session, as cards you can filter and click back to.
 */
class MessagesTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<SessionMessage>()
    private val list = ViewportWidthList(model)
    private val search = SearchTextField(false)
    private var messages: List<SessionMessage> = emptyList()
    private var hoveredImage: String? = null

    init {
        list.cellRenderer = MessageCardRenderer()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.fixedCellHeight = -1
        list.emptyText.text = "No messages in this session yet"

        search.textEditor.emptyText.text = "Filter messages"
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = applyFilter()
        })

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val message = messageAt(event) ?: return

                MessagePreview.show(list, event.point, message) { jumpTo(message) }
            }

            override fun mouseExited(event: MouseEvent) = ImagePreview.hide()
        })

        // Thumbnails are 72px: readable enough to spot the right message, never enough to read.
        list.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                val image = imageAt(event)
                if (image == null) return ImagePreview.hide()
                if (image == hoveredImage) return

                hoveredImage = image
                ImagePreview.show(list, event.point, image)
            }
        })

        // The cards wrap to a width baked in when they render, so a resize has to redraw them.
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                list.fixedCellHeight = -1
                list.revalidate()
                list.repaint()
            }
        })

        add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(search, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        messages = insight.messages
        applyFilter()
    }

    private fun applyFilter() {
        val query = search.text.trim()

        model.clear()
        messages.filter { it.text.contains(query, ignoreCase = true) }.forEach { model.addElement(it) }
    }

    private fun messageAt(event: MouseEvent): SessionMessage? {
        val index = list.locationToIndex(event.point)
        if (index < 0 || !list.getCellBounds(index, index).contains(event.point)) return null

        return model.getElementAt(index)
    }

    /** The thumbnail strip sits in the bottom band of a card, which is enough to place the cursor. */
    private fun imageAt(event: MouseEvent): String? {
        val index = list.locationToIndex(event.point)
        if (index < 0) return null
        val bounds = list.getCellBounds(index, index) ?: return null
        if (!bounds.contains(event.point)) return null
        val message = model.getElementAt(index)
        if (message.images.isEmpty()) return null

        val fromBottom = bounds.y + bounds.height - event.point.y
        if (fromBottom > com.intellij.util.ui.JBUI.scale(96)) return null
        val column = ((event.point.x - bounds.x - com.intellij.util.ui.JBUI.scale(44)) / com.intellij.util.ui.JBUI.scale(78))

        return message.images.getOrNull(column.coerceAtLeast(0))
    }

    /** Scrolling the terminal is best effort: a message older than the scrollback is simply gone. */
    private fun jumpTo(message: SessionMessage) {
        val terminal = selectedSession()
            ?.let { session -> FileEditorManager.getInstance(project).getEditors(session) }
            ?.filterIsInstance<ClaudeSessionEditor>()
            ?.firstOrNull()
            ?.terminal()
            ?: return

        // Silently: the preview is already open, and a balloon per click for the common case is noise.
        ApplicationManager.getApplication().executeOnPooledThread { TerminalScroll.scrollTo(terminal, message.headline) }
    }
}
