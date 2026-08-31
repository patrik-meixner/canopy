package com.canopy.toolwindow

import com.canopy.model.SessionDisplay
import com.canopy.util.CanopyExecutor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Everything one session changed, across every repository it wrote to.
 *
 * IntelliJ's own Commit window is bound to a single repository, so a session that edited the
 * superproject and two submodules cannot be reviewed in one place. The working trees are derived
 * from the paths the session wrote, which covers submodules and worktrees without being told about
 * either.
 */
class SessionDetailPanel(
    private val project: Project,
    parent: Disposable,
    private val resolveSession: (String) -> SessionDisplay?
) : JPanel(BorderLayout()), Disposable {

    private val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply { isOpaque = false }
    private val placeholder = JBLabel("Open a session to review its changes", SwingConstants.CENTER).apply {
        foreground = UIUtil.getLabelDisabledForeground()
        border = JBUI.Borders.empty(16)
    }
    private val browser = SectionedChangesBrowser(
        project,
        onRefreshRequested = { refresh(force = true) },
        onCommitRequested = ::commitEverything,
        onPushRequested = ::pushEverything,
        onOverlapsRequested = ::showOverlaps,
        onNoteRequested = ::sendNote,
        onProblemsRequested = ::sendProblems,
        onRestoreRequested = ::restoreCheckpoint,
        onCommitSelectionRequested = ::commitSelection,
        onPushSelectionRequested = ::pushSelection
    ).apply { border = JBUI.Borders.empty() }

    /** Recomputing on every tab switch made going back and forth re-run git each time. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, SessionChangeSet>()

    /** Bumped per request so a slow scan for a session you already moved off cannot overwrite the view. */
    private val generation = AtomicLong()
    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(SessionDetailPanel::class.java)

    @Volatile private var shownSessionId: String? = null
    @Volatile private var shownSignature: String? = null
    @Volatile private var lastRevalidatedAt = 0L

    /**
     * [JBLoadingPanel] delegates `add` to an inner panel but inherits `removeAll`, so swapping its
     * children directly tears out the loading layer itself. One card stack added once avoids that.
     */
    /**
     * The tree says which files are in which state; the header says whether anything needs doing,
     * and which session is being reviewed, without expanding four sections to find out.
     */
    private val headline = com.intellij.ui.components.JBLabel().apply {
        font = font.deriveFont(java.awt.Font.BOLD)
    }
    private val outstanding = com.intellij.ui.components.JBLabel().apply {
        foreground = com.intellij.util.ui.UIUtil.getLabelDisabledForeground()
        font = com.intellij.util.ui.UIUtil.getLabelFont(com.intellij.util.ui.UIUtil.FontSize.SMALL)
    }
    private val header = com.canopy.insight.IslandPanel(BorderLayout(0, JBUI.scale(3))).apply {
        border = JBUI.Borders.empty(8, 12)
        add(headline, BorderLayout.NORTH)
        add(outstanding, BorderLayout.SOUTH)
    }

    private val cards = JPanel(java.awt.CardLayout()).apply {
        isOpaque = false
        add(placeholder, PLACEHOLDER_CARD)
        add(browser, BROWSER_CARD)
    }

    init {
        Disposer.register(parent, this)

        loadingPanel.setLoadingText("Collecting changes")
        loadingPanel.add(cards, BorderLayout.CENTER)
        add(header, BorderLayout.NORTH)
        add(loadingPanel, BorderLayout.CENTER)

        addHierarchyListener { event ->
            if (event.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) {
                return@addHierarchyListener
            }
            if (!isShowing) return@addHierarchyListener

            refresh(force = false)
        }
    }

    /** Resolution reads every transcript, so even finding the session has to stay off the EDT. */
    fun showSession(sessionId: String?) {
        // Opening a diff selects a non-session tab; keeping the last session is what makes the
        // panel usable while clicking through files.
        if (sessionId == null || sessionId == shownSessionId) return

        shownSessionId = sessionId
        shownSignature = null

        val cached = cache[sessionId]
        if (cached != null) render(cached)

        refresh(force = true)
    }

    /**
     * Cached results show immediately and are then confirmed against git in the background, so the
     * panel is never blank waiting and never quietly stale. Only a changed result redraws.
     */
    fun refresh(force: Boolean) {
        val sessionId = shownSessionId ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastRevalidatedAt < com.canopy.settings.CanopySettings.getInstance().state.detailRevalidateSeconds * 1000L) return

        lastRevalidatedAt = now
        val request = generation.incrementAndGet()
        val hasContent = cache[sessionId] != null

        if (!hasContent) loadingPanel.startLoading()
        com.canopy.services.RepoScopeService.getInstance(project).refreshIfStale { }

        CanopyExecutor.submit {
            // Without this the spinner would outlive any git failure, since the render that
            // clears it only runs on the success path.
            val changeSet = try {
                collectChanges(resolveSession(sessionId))
            } catch (e: Exception) {
                log.warn("Failed to collect changes for session $sessionId", e)
                EMPTY_CHANGES
            }
            cache[sessionId] = changeSet
            ApplicationManager.getApplication().invokeLater {
                if (request != generation.get() || Disposer.isDisposed(this)) return@invokeLater
                if (sessionId != shownSessionId) return@invokeLater

                render(changeSet)
            }
        }
    }

    /**
     * Agents edit through the shell as often as through the edit tools, so the written paths are a
     * refinement rather than a source of truth: with none recorded, every repo in the workspace is
     * reviewed instead of claiming the session changed nothing.
     */
    private fun collectChanges(session: SessionDisplay?): SessionChangeSet {
        val scope = changeScopeFor(session, workspaceRoots())
        val collected = scope.roots.map { SessionChanges.collect(it, scope.since) }

        return SessionChangeSet(
            changes = SessionChangeSection.entries.associateWith { section ->
                collected.flatMap { it.changes[section].orEmpty() }
            }.filterValues { it.isNotEmpty() },
            unversioned = if (com.canopy.settings.CanopySettings.getInstance().state.showUnversionedInDetail) collected.flatMap { it.unversioned } else emptyList(),
            pushedCommits = collected.flatMap { it.pushedCommits }.sortedByDescending { it.commit.atMillis }
        )
    }

    /** Commits every repository the session touched, then re-reads so the sections move. */
    private fun commitEverything() {
        val sessionId = shownSessionId ?: return

        // Asking first keeps resolving the repositories — which reads transcripts — off the EDT.
        val message = com.intellij.openapi.ui.Messages.showMultilineInputDialog(
            project,
            "Commit message for every repository this session changed:",
            "Commit Session Changes",
            null,
            null,
            null
        )?.trim().orEmpty()
        if (message.isEmpty()) return

        CanopyExecutor.submit {
            val outcomes = SessionCommit.commit(rootsOf(sessionId).toList(), message, project.basePath)
            ApplicationManager.getApplication().invokeLater {
                reportCommit(outcomes)
                refresh(force = true)
            }
        }
    }

    private fun reportCommit(outcomes: List<CommitOutcome>) {
        val failed = outcomes.filterNot { it.ok }
        if (failed.isEmpty()) return

        com.intellij.openapi.ui.Messages.showErrorDialog(
            project,
            failed.joinToString("\n\n") { "${it.root}:\n${it.detail.take(400)}" },
            "Commit Failed"
        )
    }

    private fun commitSelection() {
        val sessionId = shownSessionId ?: return
        val paths = browser.selectedPaths()
        if (paths.isEmpty()) return

        val message = com.intellij.openapi.ui.Messages.showMultilineInputDialog(
            project,
            "Commit message for the ${paths.size} selected file(s):",
            "Commit Selected Files",
            null,
            null,
            null
        )?.trim().orEmpty()
        if (message.isEmpty()) return

        CanopyExecutor.submit {
            val grouped = groupPathsByRoot(paths, rootsOf(sessionId))
            val outcomes = SessionCommit.commitPaths(grouped, message, project.basePath)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                reportCommit(outcomes)
                refresh(force = true)
            }
        }
    }

    private fun pushSelection() {
        val sessionId = shownSessionId ?: return
        val paths = browser.selectedPaths()
        if (paths.isEmpty()) return

        CanopyExecutor.submit {
            val roots = groupPathsByRoot(paths, rootsOf(sessionId)).keys.toList()
            val outcomes = SessionPush.push(roots, project.basePath)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                reportPush(outcomes)
                refresh(force = true)
            }
        }
    }

    /** Ends the review where it actually ends: on the remote, with the link to open the request. */
    private fun pushEverything() {
        val sessionId = shownSessionId ?: return

        CanopyExecutor.submit {
            val outcomes = SessionPush.push(rootsOf(sessionId).toList(), project.basePath)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater

                reportPush(outcomes)
                refresh(force = true)
            }
        }
    }

    private fun reportPush(outcomes: List<PushOutcome>) {
        val failed = outcomes.filterNot { it.ok }
        if (failed.isNotEmpty()) {
            return com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                failed.joinToString("\n\n") { "${it.root}:\n${it.detail.take(400)}" },
                "Push Failed"
            )
        }

        val notification = com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("Canopy")
            .createNotification("Pushed ${outcomes.size} repository(s)", com.intellij.notification.NotificationType.INFORMATION)

        for (outcome in outcomes.mapNotNull { it.reviewUrl?.let { url -> it.root to url } }) {
            notification.addAction(object : com.intellij.openapi.project.DumbAwareAction("Open Request for ${Path.of(outcome.first).fileName}") {
                override fun actionPerformed(event: com.intellij.openapi.actionSystem.AnActionEvent) =
                    com.intellij.ide.BrowserUtil.browse(outcome.second)
            })
        }
        notification.notify(project)
    }

    /** Two agents editing one file is the failure that costs an afternoon, and nothing else sees it. */
    private fun showOverlaps() {
        val sessionId = shownSessionId ?: return
        val found = com.canopy.services.SessionFileIndex.getInstance(project).overlapsFor(sessionId)
        val message = if (found.isEmpty()) {
            "No other recently parsed session has written to these files"
        } else {
            found.entries.joinToString("<br>") { (path, sharers) ->
                "${Path.of(path).fileName}: also ${sharers.mapNotNull { resolveSession(it)?.displayName ?: it }.joinToString(", ")}"
            }
        }

        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("Canopy")
            .createNotification(message, com.intellij.notification.NotificationType.INFORMATION)
            .notify(project)
    }

    /** Restoring writes over the working tree, so it names what it will do and asks first. */
    private fun restoreCheckpoint() {
        val sessionId = shownSessionId ?: return
        val checkpoints = com.canopy.services.SessionCheckpoints.getInstance(project).list(sessionId)
        if (checkpoints.isEmpty()) {
            return com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "This session has no checkpoints yet. One is taken each time it finishes a turn.",
                "No Checkpoints"
            )
        }

        val chosen = com.intellij.openapi.ui.Messages.showEditableChooseDialog(
            "Put the working tree back as it stood at:",
            "Restore a Checkpoint",
            null,
            checkpoints.map { it.label }.toTypedArray(),
            checkpoints.first().label,
            null
        ) ?: return
        val checkpoint = checkpoints.firstOrNull { it.label == chosen } ?: return

        val confirmed = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            "Files as they were at ${checkpoint.label} will overwrite what is on disk now. " +
                "Files created since are left alone.",
            "Restore Checkpoint",
            null
        )
        if (confirmed != com.intellij.openapi.ui.Messages.YES) return

        CanopyExecutor.submit {
            val (ok, output) = com.canopy.services.SessionCheckpoints.getInstance(project).restore(sessionId, checkpoint)

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (!ok) com.intellij.openapi.ui.Messages.showErrorDialog(project, output.take(400), "Restore Failed")
                com.intellij.openapi.vfs.VirtualFileManager.getInstance().asyncRefresh(null)
                refresh(force = true)
            }
        }
    }

    /** The agent cannot see the IDE's analysis, so it ships code the editor is already underlining. */
    private fun sendProblems() {
        val sessionId = shownSessionId ?: return
        val roots = rootsOf(sessionId)
        val paths = browser.selectedPaths().ifEmpty { browser.allPaths() }
        val problems = TouchedFileProblems.collect(project, paths)
        val prompt = problemPrompt(problems, roots)
        if (prompt == null) {
            return com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "The IDE reports nothing in these files. Note that only files with an open editor have been analysed.",
                "No Problems Found"
            )
        }

        com.canopy.editor.SessionInput.send(project, sessionId, prompt)
    }

    /** The note goes to the session this tab is reviewing, so the diff never has to be left. */
    private fun sendNote() {
        val sessionId = shownSessionId ?: return
        val roots = rootsOf(sessionId)
        val paths = browser.selectedPaths()
        if (paths.isEmpty()) {
            return com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Select the files the note is about.",
                "Nothing Selected"
            )
        }

        val dialog = ReviewNoteDialog(project, paths, null, roots)
        if (!dialog.showAndGet()) return
        val prompt = dialog.prompt() ?: return

        com.canopy.editor.SessionInput.send(project, sessionId, prompt)
    }

    private fun rootsOf(sessionId: String): Set<String> =
        changeScopeFor(resolveSession(sessionId), workspaceRoots()).roots

    private fun workspaceRoots(): Set<String> {
        val scopes = com.canopy.services.RepoScopeService.getInstance(project).cachedScopes()
        if (scopes.isNotEmpty()) return scopes.map { it.root }.toSet()

        return setOfNotNull(project.basePath)
    }

    /** An unchanged result must not redraw: rebuilding the tree would drop scroll and selection. */
    private fun render(changeSet: SessionChangeSet) {
        loadingPanel.stopLoading()
        showCard(BROWSER_CARD)

        val signature = changeSet.signature()
        if (signature == shownSignature) return

        shownSignature = signature
        updateHeader(changeSet)
        browser.setSections(changeSet)
    }

    private fun updateHeader(changeSet: SessionChangeSet) {
        val session = shownSessionId?.let(resolveSession)
        val repositories = shownSessionId?.let { rootsOf(it).size } ?: 0

        headline.text = sessionHeadline(session)
        outstanding.text = outstandingSummary(
            uncommitted = changeSet.changes[SessionChangeSection.Uncommitted].orEmpty().size,
            untracked = changeSet.unversioned.size,
            unpushed = changeSet.changes[SessionChangeSection.Committed].orEmpty().size,
            pushed = changeSet.changes[SessionChangeSection.Pushed].orEmpty().size,
            repositories = repositories
        )
    }

    private fun showCard(name: String) {
        (cards.layout as java.awt.CardLayout).show(cards, name)
    }

    override fun dispose() = Unit

    private companion object {
        const val PLACEHOLDER_CARD = "placeholder"
        const val BROWSER_CARD = "browser"
        val EMPTY_CHANGES = SessionChangeSet(emptyMap(), emptyList())
    }
}

data class ChangeScope(val roots: Set<String>, val since: java.time.Instant?)

/**
 * What a Detail tab reviews.
 *
 * A session with no transcript yet cannot be resolved, and reviewing nothing was wrong: uncommitted
 * work exists before the first message and is the reason the session was started. Without a session
 * the whole workspace is in scope and nothing is bounded by time, since there is no start to bound
 * it by.
 */
internal fun changeScopeFor(session: com.canopy.model.SessionDisplay?, workspaceRoots: Set<String>): ChangeScope {
    if (session == null) return ChangeScope(workspaceRoots, null)

    return ChangeScope(session.touchedRoots.keys.ifEmpty { workspaceRoots }, session.startedAt)
}
