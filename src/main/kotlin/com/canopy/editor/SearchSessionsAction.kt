package com.canopy.editor

import com.canopy.services.ClaudeSessionService
import com.canopy.services.TranscriptHit
import com.canopy.services.TranscriptSearch
import com.canopy.services.snippetAround
import com.canopy.settings.CanopySettings
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Finds the session where something was said, by what was said in it.
 *
 * Sessions are named after their first prompt, which is a poor index of what they became.
 */
class SearchSessionsAction : AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        SearchSessionsDialog(project).show()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
        event.presentation.text = "Search Sessions…"
        event.presentation.description = "Search every session by what was said in it"
        event.presentation.icon = AllIcons.Actions.Search
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class SearchSessionsDialog(private val project: Project) : DialogWrapper(project) {

    private val model = DefaultListModel<TranscriptHit>()
    private val results = JBList(model)
    private val search = SearchTextField(false)
    private var running: AtomicBoolean? = null

    init {
        title = "Search Sessions"
        setOKButtonText("Open")
        init()

        results.cellRenderer = HitRenderer(
            nameOf = { id -> ClaudeSessionService.getInstance(project).cachedDisplayName(id) },
            queryOf = { search.text.trim() }
        )
        results.emptyText.text = "Type at least two characters"
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = schedule()
        })

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                openSelected()
                return true
            }
        }.installOn(results)
    }

    override fun getPreferredFocusedComponent(): JComponent = search

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(JBUI.scale(680), JBUI.scale(420))
        border = JBUI.Borders.empty(6)
        add(search, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(results, true), BorderLayout.CENTER)
    }

    /** Each keystroke cancels the scan the last one started, so typing never queues up work. */
    private fun schedule() {
        running?.set(true)
        val cancelled = AtomicBoolean(false)
        running = cancelled
        val query = search.text
        val basePath = project.basePath ?: return
        val days = CanopySettings.getInstance().state.recentSessionDays

        CanopyExecutor.submit {
            val hits = TranscriptSearch.search(basePath, query, days, cancelled)
            if (cancelled.get()) return@submit

            ApplicationManager.getApplication().invokeLater {
                if (cancelled.get() || project.isDisposed) return@invokeLater

                model.clear()
                hits.forEach { model.addElement(it) }
                results.emptyText.text = if (query.trim().length < 2) "Type at least two characters" else "Nothing found"
            }
        }
    }

    override fun doOKAction() {
        openSelected()
        super.doOKAction()
    }

    private fun openSelected() {
        val hit = results.selectedValue ?: return
        val manager = FileEditorManager.getInstance(project)
        val open = manager.openFiles.filterIsInstance<ClaudeSessionVirtualFile>().firstOrNull { it.sessionId == hit.sessionId }
        val name = ClaudeSessionService.getInstance(project).cachedDisplayName(hit.sessionId) ?: hit.sessionId

        manager.openFile(open ?: ClaudeSessionVirtualFile(name, sessionId = hit.sessionId), true)
    }

    private class HitRenderer(
        private val nameOf: (String) -> String?,
        private val queryOf: () -> String
    ) : ColoredListCellRenderer<TranscriptHit>() {
        override fun customizeCellRenderer(
            list: JList<out TranscriptHit>,
            value: TranscriptHit,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = AllIcons.Actions.Search
            append(nameOf(value.sessionId) ?: value.sessionId, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            append("  #${value.ordinal}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            append("   ${snippetAround(value.text, queryOf())}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
}
