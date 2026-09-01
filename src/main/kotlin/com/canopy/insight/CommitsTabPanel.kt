package com.canopy.insight

import com.canopy.toolwindow.CommitEntry
import com.canopy.toolwindow.SessionCommits
import com.canopy.util.CanopyExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
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
    private val split = JBSplitter(true, "canopy.commits.split", 0.45f)
    private val body = ContentOrEmpty(
        split,
        EmptyState(
            AllIcons.Vcs.CommitNode,
            "No commits yet",
            "What this session committed, in every repository it wrote to, with the files behind each one."
        )
    )
    private val more = MoreRow {
        limit += COMMIT_PAGE
        showPage()
    }

    init {
        background = InsightUi.panelBackground()
        commits.cellRenderer = CommitRenderer()
        commits.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        commits.emptyText.text = "This session has committed nothing yet"
        commits.border = JBUI.Borders.empty(InsightUi.GAP, InsightUi.GAP)
        commits.addListSelectionListener { if (!it.valueIsAdjusting) showFilesOf(commits.selectedValuesList) }

        files.hideViewerBorder()

        com.canopy.services.WorkspaceChangeNotifier.getInstance(project).addListener(parent) {
            if (isShowing && loadedRoots.isNotEmpty()) reload(loadedRoots)
        }

        split.firstComponent = JPanelWithMore(ScrollPaneFactory.createScrollPane(commits, true), more)
        split.secondComponent = files

        add(toolbar().component, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
    }

    /** The tab follows the working trees on its own; this is for a commit made outside them. */
    private fun toolbar(): com.intellij.openapi.actionSystem.ActionToolbar {
        val refresh = object : com.intellij.openapi.actionSystem.AnAction(
            "Refresh",
            "Re-read the commits of this session",
            com.intellij.icons.AllIcons.Actions.Refresh
        ), com.intellij.openapi.project.DumbAware {
            override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) {
                loadedFor = null
                if (loadedRoots.isNotEmpty()) reload(loadedRoots)
            }

            override fun update(event: com.intellij.openapi.actionSystem.AnActionEvent) {
                event.presentation.isEnabled = loadedRoots.isNotEmpty()
            }

            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
        }

        return com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .createActionToolbar(
                "CanopyCommitsToolbar",
                com.intellij.openapi.actionSystem.DefaultActionGroup(refresh),
                true
            )
            .also { it.targetComponent = this }
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
        val kept = commits.selectedValuesList.mapTo(HashSet()) { it.sha }
        val page = pageful(collected, limit)

        more.show(page.remaining, COMMIT_PAGE)
        body.showEmpty(collected.isEmpty())
        model.clear()
        page.shown.forEach { model.addElement(it) }
        // Every commit that was selected and is still listed, so a reload keeps a whole range.
        val rows = (0 until model.size()).filter { model[it].sha in kept }
        if (rows.isNotEmpty()) commits.selectedIndices = rows.toIntArray()
    }

    /** One git log per repository the session wrote to, off the EDT, newest commit first. */
    private fun reload(roots: List<Pair<String, String>>) {
        val since = insightStart()

        CanopyExecutor.submit {
            // One git log per repository, and they wait on the disk rather than on each other.
            val logs = roots
                .mapNotNull { (repository, path) -> repositoryRootOf(path)?.let { repository to it } }
                .distinctBy { it.second }
                .map { (repository, root) ->
                    java.util.concurrent.Callable { SessionCommits.list(root, repository, since) }
                }
            val collected = com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService()
                .invokeAll(logs)
                .flatMap { it.get() }
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

    private fun repositoryRootOf(path: String): String? =
        com.canopy.util.GitRootCache.rootOf(com.canopy.util.directoryOf(java.nio.file.Path.of(path)))

    /** Several commits answer as one set of files, which is what a range of them means. */
    private fun showFilesOf(selected: List<CommitEntry>) {
        if (selected.isEmpty()) return files.setChangesToDisplay(emptyList())

        val shas = selected.map { it.sha }

        CanopyExecutor.submit {
            val perCommit = selected.flatMap { com.canopy.toolwindow.SessionCommits.changesIn(it.root, it.sha) }
            val changes = com.canopy.toolwindow.unionByPath(perCommit) {
                it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || commits.selectedValuesList.map { it.sha } != shas) return@invokeLater

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
