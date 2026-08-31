package com.canopy.insight

import com.canopy.toolwindow.CommitEntry
import com.canopy.toolwindow.SessionCommits
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * The commits a session made, and the files behind whichever one is selected.
 *
 * The Log window answers this per repository and per branch. A session's commits span whatever it
 * worked in, so they are collected per repository it wrote to and bounded by when it started.
 */
class CommitsTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val model = DefaultListModel<CommitEntry>()
    private val commits = ViewportWidthList(model)
    private val files = SimpleChangesBrowser(project, false, true)
    private var loadedFor: String? = null
    private var loadedRoots: List<Pair<String, String>> = emptyList()
    private var collected: List<CommitEntry> = emptyList()
    private var limit = COMMIT_PAGE
    private val more = MoreRow {
        limit += COMMIT_PAGE
        showPage()
    }

    init {
        background = InsightUi.panelBackground()
        commits.cellRenderer = CommitRenderer()
        commits.selectionMode = ListSelectionModel.SINGLE_SELECTION
        commits.emptyText.text = "This session has committed nothing yet"
        commits.border = JBUI.Borders.empty(InsightUi.GAP, InsightUi.GAP)
        commits.addListSelectionListener { if (!it.valueIsAdjusting) showFilesOf(commits.selectedValue) }

        files.hideViewerBorder()

        com.canopy.services.WorkspaceChangeNotifier.getInstance(project).addListener(parent) {
            if (isShowing && loadedRoots.isNotEmpty()) reload(loadedRoots)
        }

        add(JBSplitter(true, "canopy.commits.split", 0.45f).apply {
            firstComponent = JPanelWithMore(ScrollPaneFactory.createScrollPane(commits, true), more)
            secondComponent = files
        }, BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        val session = selectedSession()?.sessionId ?: return
        val roots = insight.files.map { it.repository to it.path }.distinctBy { it.first }
        val signature = "$session:${insight.revision}"
        if (signature == loadedFor) return
        loadedFor = signature
        loadedRoots = roots

        reload(roots)
    }

    private fun showPage() {
        val kept = commits.selectedValue?.sha
        val page = pageful(collected, limit)

        more.show(page.remaining, COMMIT_PAGE)
        model.clear()
        page.shown.forEach { model.addElement(it) }
        (0 until model.size()).firstOrNull { model[it].sha == kept }?.let(commits::setSelectedIndex)
    }

    /** One git log per repository the session wrote to, off the EDT, newest commit first. */
    private fun reload(roots: List<Pair<String, String>>) {
        val since = insightStart()

        CanopyExecutor.submit {
            val collected = roots
                .mapNotNull { (repository, path) -> repositoryRootOf(path)?.let { repository to it } }
                .distinctBy { it.second }
                .flatMap { (repository, root) -> SessionCommits.list(root, repository, since) }
                .sortedByDescending { it.atMillis }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                this.collected = collected
                showPage()
            }
        }
    }

    private fun insightStart(): java.time.Instant? {
        val sessionId = selectedSession()?.sessionId ?: return null

        return com.canopy.services.ClaudeSessionService.getInstance(project).cachedStartedAt(sessionId)
    }

    private fun repositoryRootOf(path: String): String? {
        var current: java.nio.file.Path? = java.nio.file.Path.of(path).parent
        while (current != null) {
            if (java.nio.file.Files.exists(current.resolve(".git"))) return current.toString()
            current = current.parent
        }

        return null
    }

    private fun showFilesOf(commit: CommitEntry?) {
        if (commit == null) return files.setChangesToDisplay(emptyList())

        CanopyExecutor.submit {
            val changes: List<Change> = SessionCommits.changesIn(commit.root, commit.sha)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || commits.selectedValue?.sha != commit.sha) return@invokeLater

                files.setChangesToDisplay(changes)
            }
        }
    }

    private class CommitRenderer : ColoredListCellRenderer<CommitEntry>() {
        override fun customizeCellRenderer(
            list: JList<out CommitEntry>,
            value: CommitEntry,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = AllIcons.Vcs.CommitNode
            append(value.subject, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("   ${value.repository}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            append("  ${com.canopy.toolwindow.relativeAge(value.atMillis, System.currentTimeMillis())}",
                SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            toolTipText = "${value.sha.take(8)}  ${value.author}"
        }
    }
}

private const val COMMIT_PAGE = 50
