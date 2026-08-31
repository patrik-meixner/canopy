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
        onOverlapsRequested = ::showOverlaps
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
    private val cards = JPanel(java.awt.CardLayout()).apply {
        isOpaque = false
        add(placeholder, PLACEHOLDER_CARD)
        add(browser, BROWSER_CARD)
    }

    init {
        Disposer.register(parent, this)

        loadingPanel.setLoadingText("Collecting changes")
        loadingPanel.add(cards, BorderLayout.CENTER)
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
            unversioned = if (com.canopy.settings.CanopySettings.getInstance().state.showUnversionedInDetail) collected.flatMap { it.unversioned } else emptyList()
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
                val failed = outcomes.filterNot { it.ok }
                if (failed.isNotEmpty()) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        failed.joinToString("\n\n") { "${it.root}:\n${it.detail.take(400)}" },
                        "Commit Failed"
                    )
                }
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
        browser.setSections(changeSet)
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
