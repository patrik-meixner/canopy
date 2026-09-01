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
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * What you asked this session, as cards you can filter, read in full and click back to.
 */
class MessagesTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val cards = CardListPanel(
        EmptyState(
            com.intellij.icons.AllIcons.General.Balloon,
            "Nothing said here yet",
            "Everything you ask this session shows up here, searchable, and clicks back to where it was said."
        )
    )
    private val search = SearchTextField(false)
    private var messages: List<SessionMessage> = emptyList()
    private var limit = MESSAGE_PAGE
    private var boundSession: String? = null

    init {
        background = InsightUi.panelBackground()
        search.textEditor.emptyText.text = "Filter messages"
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                limit = MESSAGE_PAGE
                rebuild()
            }
        })

        add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 6)
            background = InsightUi.panelBackground()
            add(search, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        val session = selectedSession()?.sessionId
        val changedSession = session != boundSession
        boundSession = session

        messages = com.canopy.toolwindow.withoutRepeats(insight.messages)
        rebuild()
        // A different session is a different conversation, and you read a conversation from its end.
        if (changedSession) cards.followNewest()
    }

    private fun rebuild() {
        val query = search.text.trim()
        val matching = messages.filter { it.text.contains(query, ignoreCase = true) }
        // The newest are the ones being read, so a page is taken from the end.
        val page = pageful(matching.asReversed(), limit)
        val shown = page.shown.asReversed()

        cards.onMore(page.remaining, MESSAGE_PAGE, atTop = true) {
            limit += MESSAGE_PAGE
            rebuild()
        }

        cards.setCards(
            shown.map { message ->
                MessageCard(
                    message,
                    onOpen = { card, event -> open(card, event, message) },
                    onJump = { jumpTo(message) }
                )
            },
            shown.map { "${it.ordinal}:${it.text.length}" }
        )
    }

    private fun open(card: MessageCard, event: java.awt.event.MouseEvent, message: SessionMessage) {
        MessagePreview.show(card, event.point, message) { jumpTo(message) }
    }

    /** A message older than the scrollback is genuinely gone, which is worth saying rather than
     *  looking like the click did nothing. */
    private fun jumpTo(message: SessionMessage) {
        val terminal = selectedSession()
            ?.let { session -> FileEditorManager.getInstance(project).getEditors(session) }
            ?.filterIsInstance<ClaudeSessionEditor>()
            ?.firstOrNull()
            ?.terminal()
            ?: return notFound("This session has no open terminal tab.")

        ApplicationManager.getApplication().executeOnPooledThread {
            val found = TerminalScroll.scrollTo(terminal, message.headline)
            if (found) return@executeOnPooledThread

            ApplicationManager.getApplication().invokeLater {
                notFound("That message is no longer in the terminal's scrollback.")
            }
        }
    }

    private fun notFound(reason: String) {
        com.intellij.openapi.ui.Messages.showInfoMessage(project, reason, "Cannot Scroll There")
    }
}

private const val MESSAGE_PAGE = 40
