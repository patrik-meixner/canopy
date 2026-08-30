package com.canopy.insight

import com.canopy.editor.ClaudeSessionEditor
import com.canopy.toolwindow.MessageCardRenderer
import com.canopy.toolwindow.SessionMessage
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
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
    private val list = JBList(model)
    private val search = SearchTextField(false)
    private var messages: List<SessionMessage> = emptyList()

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
                val index = list.locationToIndex(event.point)
                if (index < 0 || !list.getCellBounds(index, index).contains(event.point)) return

                jumpTo(model.getElementAt(index))
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

    /** Scrolling the terminal is best effort: a message older than the scrollback is simply gone. */
    private fun jumpTo(message: SessionMessage) {
        val terminal = selectedSession()
            ?.let { session -> FileEditorManager.getInstance(project).getEditors(session) }
            ?.filterIsInstance<ClaudeSessionEditor>()
            ?.firstOrNull()
            ?.terminal()
            ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val found = TerminalScroll.scrollTo(terminal, message.headline)

            if (!found) ApplicationManager.getApplication().invokeLater { reportMissing() }
        }
    }

    private fun reportMissing() {
        if (project.isDisposed) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Canopy")
            .createNotification("That message has scrolled out of the terminal's history", NotificationType.INFORMATION)
            .notify(project)
    }
}
