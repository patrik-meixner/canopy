package com.canopy.insight

import com.canopy.editor.ClaudeSessionEditor
import com.canopy.toolwindow.SessionMessage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * What you asked this session, as cards you can filter, read in full and click back to.
 */
class MessagesTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val cards = CardListPanel("No messages in this session yet")
    private val search = SearchTextField(false)
    private var messages: List<SessionMessage> = emptyList()

    init {
        background = UIUtil.getListBackground()
        search.textEditor.emptyText.text = "Filter messages"
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = rebuild()
        })

        add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 6)
            background = UIUtil.getListBackground()
            add(search, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        messages = com.canopy.toolwindow.withoutRepeats(insight.messages)
        rebuild()
    }

    private fun rebuild() {
        val query = search.text.trim()

        cards.setCards(
            messages
                .filter { it.text.contains(query, ignoreCase = true) }
                .map { message -> MessageCard(message) { card, event -> open(card, event, message) } }
        )
    }

    private fun open(card: MessageCard, event: java.awt.event.MouseEvent, message: SessionMessage) {
        MessagePreview.show(card, event.point, message) { jumpTo(message) }
    }

    /** Scrolling the terminal is best effort: a message older than the scrollback is simply gone. */
    private fun jumpTo(message: SessionMessage) {
        val terminal = selectedSession()
            ?.let { session -> FileEditorManager.getInstance(project).getEditors(session) }
            ?.filterIsInstance<ClaudeSessionEditor>()
            ?.firstOrNull()
            ?.terminal()
            ?: return

        ApplicationManager.getApplication().executeOnPooledThread { TerminalScroll.scrollTo(terminal, message.headline) }
    }
}
