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
    private val placeholder = com.canopy.insight.EmptyState(
        com.intellij.icons.AllIcons.Actions.Diff,
        "No session selected",
        "Open a session and this reviews everything it changed, across every repository it wrote to."
    )
    private val nothingOutstanding = com.canopy.insight.EmptyState(
        com.intellij.icons.AllIcons.General.InspectionsOK,
        "Nothing outstanding",
        "This session has left no uncommitted, unpushed or untracked work behind."
    )
    private val browser = SectionedChangesBrowser(
        project,
        this,
        onRefreshRequested = { refresh(force = true) },
        onCommitRequested = ::commitEverything,
        onPushRequested = ::pushEverything,
        onOverlapsRequested = ::showOverlaps,
        onNoteRequested = ::sendNote,
        onProblemsRequested = ::sendProblems,
        onCommitSelectionRequested = ::commitSelection,
        onPushSelectionRequested = ::pushSelection
    ).apply { border = JBUI.Borders.empty() }

    /**
     * Recomputing on every tab switch made going back and forth re-run git each time, so the last
     * answer for a session is shown at once and confirmed behind you.
     *
     * Bounded: a change set holds a revision per file, and a day of review visits a lot of
     * sessions. Access ordered, so what falls out is what nobody has come back to.
     */
    private val cache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, SessionChangeSet>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, SessionChangeSet>) = size > REMEMBERED
        }
    )

    /** Bumped per request so a slow scan for a session you already moved off cannot overwrite the view. */
    private val generation = AtomicLong()
    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(SessionDetailPanel::class.java)

    @Volatile private var shownSessionId: String? = null
    @Volatile private var shownSignature: Long? = null
    @Volatile private var lastRevalidatedAt = 0L
    @Volatile private var pendingWhileHidden = false

    /**
     * [JBLoadingPanel] delegates `add` to an inner panel but inherits `removeAll`, so swapping its
     * children directly tears out the loading layer itself. One card stack added once avoids that.
     */
    /**
     * The tree says which files are in which state; the header says whether anything needs doing,
     * and which session is being reviewed, without expanding four sections to find out.
     */
    private val header = SessionHeaderCard()

    private var pending: CommitScope? = null
    private val commitPanel = SessionCommitPanel(
        project,
        this,
        onCommit = ::performCommit,
        onCancel = { pending = null }
    )

    private val cards = JPanel(java.awt.CardLayout()).apply {
        isOpaque = false
        add(placeholder, ReviewState.NoSession.name)
        add(JPanel().apply { isOpaque = false }, ReviewState.Collecting.name)
        add(nothingOutstanding, ReviewState.NothingOutstanding.name)
        add(browser, ReviewState.Changes.name)
    }

    init {
        Disposer.register(parent, this)

        loadingPanel.setLoadingText("Collecting changes")
        loadingPanel.add(cards, BorderLayout.CENTER)
        header.isVisible = false
        add(header, BorderLayout.NORTH)
        add(loadingPanel, BorderLayout.CENTER)
        add(commitPanel, BorderLayout.SOUTH)

        com.canopy.services.WorkspaceChangeNotifier.getInstance(project).addListener(this) {
            if (isShowing) refresh(force = true)
        }

        addHierarchyListener { event ->
            if (event.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) {
                return@addHierarchyListener
            }
            if (!isShowing) return@addHierarchyListener

            // Opened after a session already was: nothing told the panel which one, because the
            // selection changed while this window did not exist.
            if (shownSessionId == null) showSession(selectedSessionId())

            refresh(force = pendingWhileHidden)
        }
    }

    private fun selectedSessionId(): String? = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
        .selectedFiles
        .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
        .firstOrNull()
        ?.sessionId

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
     * Reading six working trees costs the same whether or not anyone is looking at the result, so
     * a closed review window does none of it and catches up the moment it is opened.
     */
    private fun hidden(): Boolean {
        if (isShowing) return false

        pendingWhileHidden = true

        return true
    }

    /**
     * Cached results show immediately and are then confirmed against git in the background, so the
     * panel is never blank waiting and never quietly stale. Only a changed result redraws.
     */
    fun refresh(force: Boolean) {
        val sessionId = shownSessionId ?: return
        if (hidden()) return

        pendingWhileHidden = false
        val now = System.currentTimeMillis()
        if (!force && now - lastRevalidatedAt < com.canopy.settings.CanopySettings.getInstance().state.detailRevalidateSeconds * 1000L) return

        lastRevalidatedAt = now
        val request = generation.incrementAndGet()
        val hasContent = cache[sessionId] != null

        if (!hasContent) showState(ReviewState.Collecting)
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
        // Around five git processes per repository, and a session routinely spans six of them.
        // Run sequentially that is the half second before the tree appears; they are waiting on
        // the disk, not on each other.
        val collected = com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService()
            .invokeAll(scope.roots.map { root -> java.util.concurrent.Callable { SessionChanges.collect(root, scope.since) } })
            .map { it.get() }

        return SessionChangeSet(
            changes = SessionChangeSection.entries.associateWith { section ->
                collected.flatMap { it.changes[section].orEmpty() }
            }.filterValues { it.isNotEmpty() },
            unversioned = if (com.canopy.settings.CanopySettings.getInstance().state.showUnversionedInDetail) collected.flatMap { it.unversioned } else emptyList(),
            pushedCommits = collected.flatMap { it.pushedCommits }.sortedByDescending { it.commit.atMillis }
        )
    }

    /** The message is written where the tree is, so what is being committed stays on screen. */
    private fun commitEverything() = askToCommit(CommitScope.Everything)

    private fun commitSelection() {
        val paths = browser.selectedPaths()
        if (paths.isEmpty()) return

        askToCommit(CommitScope.Selection(paths))
    }

    private fun askToCommit(scope: CommitScope) {
        val sessionId = shownSessionId ?: return

        pending = scope
        commitPanel.ask(commitCaption(scope, rootsOf(sessionId).size))
    }

    /** Resolving the repositories reads transcripts, so it waits until there is a message to use. */
    private fun performCommit(message: String, after: AfterCommit) {
        val sessionId = shownSessionId ?: return
        val scope = pending ?: return
        pending = null
        val title = if (after == AfterCommit.Push) "Committing and pushing" else "Committing"

        com.canopy.util.inBackground(
            project,
            title,
            work = { indicator ->
                val roots = rootsOf(sessionId)
                indicator.text = "Committing ${roots.size} repository(s)"
                val outcomes = when (scope) {
                    is CommitScope.Everything -> SessionCommit.commit(roots.toList(), message, project.basePath)
                    is CommitScope.Selection ->
                        SessionCommit.commitPaths(groupPathsByRoot(scope.paths, roots), message, project.basePath)
                }
                val committed = outcomes.filter { it.ok }.map { it.root }
                if (after != AfterCommit.Push || committed.isEmpty()) return@inBackground outcomes to emptyList()

                indicator.text = "Pushing ${committed.size} repository(s)"

                outcomes to SessionPush.push(committed, project.basePath)
            },
            onDone = { (outcomes, pushed) ->
                reportCommit(outcomes)
                if (pushed.isNotEmpty()) reportPush(pushed)
                refresh(force = true)
            }
        )
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

    private fun pushSelection() {
        val sessionId = shownSessionId ?: return
        val paths = browser.selectedPaths()
        if (paths.isEmpty()) return

        pushInBackground { groupPathsByRoot(paths, rootsOf(sessionId)).keys.toList() }
    }

    /** Ends the review where it actually ends: on the remote, with the link to open the request. */
    private fun pushEverything() {
        val sessionId = shownSessionId ?: return

        pushInBackground { rootsOf(sessionId).toList() }
    }

    private fun pushInBackground(rootsOf: () -> List<String>) {
        com.canopy.util.inBackground(
            project,
            "Pushing",
            work = { indicator ->
                val roots = rootsOf()
                indicator.text = "Pushing ${roots.size} repository(s)"

                SessionPush.push(roots, project.basePath)
            },
            onDone = {
                reportPush(it)
                refresh(force = true)
            }
        )
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
        val signature = changeSet.signature()
        // Filled before it is shown, so the card never appears for a paint with nothing in it.
        if (signature != shownSignature) {
            shownSignature = signature
            updateHeader(changeSet)
            browser.setSections(changeSet)
        }

        showState(if (changeSet.isEmpty) ReviewState.NothingOutstanding else ReviewState.Changes)
    }

    private fun updateHeader(changeSet: SessionChangeSet) {
        val session = shownSessionId?.let(resolveSession)
        val counts = outstandingCounts(
            uncommitted = changeSet.changes[SessionChangeSection.Uncommitted].orEmpty().size,
            untracked = changeSet.unversioned.size,
            unpushed = changeSet.changes[SessionChangeSection.Committed].orEmpty().size,
            pushed = changeSet.changes[SessionChangeSection.Pushed].orEmpty().size
        )

        // Resolved again on every paint, not captured: a SessionDisplay is a snapshot, and reading
        // a frozen one at paint time is the same staleness as freezing the state itself.
        header.show(session, counts) { currentState() }
    }

    private fun currentState(): com.canopy.model.SessionState {
        val live = shownSessionId?.let(resolveSession)

        return com.canopy.model.SessionState(attentionOf(live), presenceOf(live))
    }

    private fun attentionOf(session: SessionDisplay?): com.canopy.model.SessionAttention {
        if (session == null) return com.canopy.model.SessionAttention.None

        return com.canopy.services.sessionAttentionIn(project, session, isRunning = isOpenHere(session))
    }

    private fun presenceOf(session: SessionDisplay?): com.canopy.model.SessionPresence = when {
        session == null -> com.canopy.model.SessionPresence.Available
        isOpenHere(session) -> com.canopy.model.SessionPresence.OpenHere
        com.canopy.services.ClaudeSessionService.getInstance(project).isExternallyOpen(session.sessionId) ->
            com.canopy.model.SessionPresence.OpenElsewhere
        else -> com.canopy.model.SessionPresence.Available
    }

    private fun isOpenHere(session: SessionDisplay): Boolean =
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<com.canopy.editor.ClaudeSessionVirtualFile>()
            .any { it.sessionId == session.sessionId }

    /**
     * The spinner belongs to one state, so nothing else can be underneath it while it turns, and
     * the card belongs to the two that have something to put in it: an empty one over a spinner
     * says a session is being reviewed while the review is still being read.
     */
    private fun showState(state: ReviewState) {
        if (state == ReviewState.Collecting) loadingPanel.startLoading() else loadingPanel.stopLoading()
        header.isVisible = state == ReviewState.Changes || state == ReviewState.NothingOutstanding
        (cards.layout as java.awt.CardLayout).show(cards, state.name)
    }

    override fun dispose() = Unit

    private companion object {
        const val REMEMBERED = 8
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
