package com.canopy.services

import com.canopy.model.SessionDisplay
import com.canopy.util.ClaudePathEncoder
import com.canopy.util.ClaudeProcessDetector
import com.canopy.util.CanopyExecutor
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ClaudeSessionService(private val project: Project) : Disposable {

    private val gson = Gson()
    private val log = Logger.getInstance(ClaudeSessionService::class.java)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val pollAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pendingTranscripts = java.util.concurrent.ConcurrentHashMap.newKeySet<Path>()
    private val incrementalInFlight = java.util.concurrent.atomic.AtomicBoolean(false)


    @Volatile
    private var cachedSessions: List<SessionDisplay>? = null

    /**
     * Per-transcript incremental parse state, keyed by file.
     *
     * The session list is rebuilt on every transcript write, and transcripts reach tens of
     * megabytes — re-parsing every file from the top each time made the cost of watching a
     * session grow with its own history. Every field the list needs accumulates cleanly over
     * appended lines (counts add, the first prompt sticks, the newest title and timestamp
     * win), so each pass only parses the bytes that are new.
     */
    private val accumulators = java.util.concurrent.ConcurrentHashMap<Path, SessionAccumulator>()

    private val MAX_TOUCHED_PATHS = 2_000

    private class SessionAccumulator {
        val tail = com.canopy.util.JsonlTailReader()
        var firstPrompt: String? = null
        var customTitle: String? = null
        var messageCount = 0
        var gitBranch: String? = null
        var lastTimestamp: Instant = Instant.EPOCH
        var firstTimestamp: Instant? = null
        var turnTail: com.canopy.model.TranscriptTail? = null
        var lastPromptLine = 0
        var lastPromptAt: Instant? = null
        /** Capped: a long session can touch thousands of files and this list only feeds a summary. */
        val touchedPaths = LinkedHashSet<String>()

        /** Derived from [touchedPaths], recomputed only when that set has actually grown. */
        var roots: Map<String, Int> = emptyMap()
        var rootsFor = -1

        fun rootsOfTouched(): Map<String, Int> {
            if (rootsFor == touchedPaths.size) return roots

            rootsFor = touchedPaths.size
            roots = com.canopy.util.gitWorkingTreeRoots(touchedPaths)

            return roots
        }

        /** Drop everything: the file was rewritten, so accumulated totals no longer apply. */
        fun reset() {
            firstPrompt = null
            customTitle = null
            messageCount = 0
            gitBranch = null
            lastTimestamp = Instant.EPOCH
            firstTimestamp = null
            turnTail = null
            lastPromptLine = 0
            lastPromptAt = null
            touchedPaths.clear()
            roots = emptyMap()
            rootsFor = -1
        }
    }

    @Volatile
    private var lastKnownMtime: Long = 0

    /** Session IDs running in external terminals (not Canopy). */
    @Volatile
    private var externalSessionIds: Set<String> = emptySet()

    private val refreshInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val refreshQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Last external-session sweep; that sweep shells out to `ps`, so it gets a floor. */
    @Volatile
    private var lastExternalSweep = 0L

    fun isExternallyOpen(sessionId: String): Boolean =
        sessionId in externalSessionIds

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** Name from the cache only: a notification must never trigger a full transcript sweep. */
    fun cachedDisplayName(sessionId: String): String? =
        cachedSessions?.firstOrNull { it.sessionId == sessionId }?.displayName

    /** From the cache only: a checkpoint must not trigger a transcript sweep. */
    fun cachedWorkingDir(sessionId: String): String? =
        cachedSessions?.firstOrNull { it.sessionId == sessionId }
            ?.let { session -> session.worktreeName?.let { com.canopy.util.ClaudePathEncoder.worktreeAbsolutePath(project.basePath ?: return null, it) } ?: session.projectPath }

    /** From the cache only, so a tab render cannot trigger a transcript sweep. */
    fun cachedStartedAt(sessionId: String): java.time.Instant? =
        cachedSessions?.firstOrNull { it.sessionId == sessionId }?.startedAt

    /**
     * The cached list, and never a parse on the EDT.
     *
     * A tab icon, a row and the review header all ask for this while painting. Loading here meant
     * the first of those paints parsed every transcript in the project - well over a gigabyte on a
     * working machine - with the UI thread held for the duration. Painting gets whatever is known
     * now and a refresh behind it; anything that truly needs the list is off the EDT already.
     */
    fun getSessions(): List<SessionDisplay> {
        cachedSessions?.let { return it }
        if (ApplicationManager.getApplication().isDispatchThread) {
            refresh()

            return emptyList()
        }

        return loadSessions().also { cachedSessions = it }
    }

    /**
     * Everything the session left on disk, wherever the encoder actually put it.
     *
     * This used to delete one guessed path. Claude Code encodes a project directory by replacing
     * both `/` and `.`, and [ClaudePathEncoder.projectDir] replaces only `/`, so for any project
     * with a dot in its path the delete removed nothing at all - and the next refresh, which reads
     * every candidate directory, put the session straight back.
     */
    fun deleteSession(sessionId: String): Boolean {
        val basePath = project.basePath ?: return false
        val directories = ClaudePathEncoder.projectDirCandidates(basePath) +
            runCatching {
                ClaudePathEncoder.worktreeNames(basePath).map { ClaudePathEncoder.worktreeProjectDir(basePath, it) }
            }.getOrDefault(emptyList())

        val footprint = sessionFootprint(directories, ClaudePathEncoder.tasksDir(), sessionId)
        if (footprint.isEmpty()) log.warn("Nothing on disk for session $sessionId")

        for (path in footprint) {
            accumulators.remove(path)
            try {
                Files.walk(path).use { under -> under.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            } catch (e: Exception) {
                log.warn("Failed to delete $path", e)
                return false
            }
        }

        // Directly, rather than by asking for a refresh: a background scan already in flight would
        // otherwise overwrite the corrected cache before the listeners see it.
        cachedSessions = cachedSessions?.filter { it.sessionId != sessionId }
        SessionDigestCache.getInstance(project).retainOnly(
            cachedSessions.orEmpty().mapNotNull { transcriptPathOf(it, basePath)?.toString() }.toSet()
        )
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it() }
        }

        return true
    }

    private fun transcriptPathOf(session: SessionDisplay, basePath: String): Path? {
        val directories = if (session.worktreeName != null) {
            listOf(ClaudePathEncoder.worktreeProjectDir(basePath, session.worktreeName))
        } else {
            ClaudePathEncoder.projectDirCandidates(basePath)
        }

        return directories.map { it.resolve("${session.sessionId}.jsonl") }.firstOrNull { Files.exists(it) }
    }

    /**
     * Rebuild the session list and notify listeners.
     *
     * Always runs on a background thread: the VFS listener that drives most refreshes fires
     * in a write action on the EDT, so doing the file scan (and the external-session process
     * sweep) inline blocked the UI thread on every transcript write.
     *
     * Overlapping requests coalesce — a call arriving mid-pass sets a flag that schedules
     * exactly one more pass afterwards, rather than queueing a pass per event.
     */
    fun refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) {
            refreshQueued.set(true)
            return
        }
        CanopyExecutor.submit {
            try {
                cachedSessions = loadSessions()
                refreshExternalSessions()
                ApplicationManager.getApplication().invokeLater {
                    listeners.forEach { it() }
                }
            } finally {
                refreshInFlight.set(false)
                // State changed while we were working: run once more, not once per event.
                if (refreshQueued.compareAndSet(true, false)) refresh()
            }
        }
    }

    /**
     * Re-reads the transcripts that actually changed, rather than every transcript in the project.
     *
     * An agent writing touches one file; rebuilding the whole list meant stat-ing sixty of them for
     * each write. Bursts coalesce without waiting: a path arriving mid-pass joins the next round of
     * the same task, so nothing is delayed and nothing is done twice.
     */
    fun refreshTranscripts(paths: Set<Path>) {
        pendingTranscripts.addAll(paths)
        if (!incrementalInFlight.compareAndSet(false, true)) return

        CanopyExecutor.submit {
            try {
                while (true) {
                    val batch = pendingTranscripts.toSet()
                    if (batch.isEmpty()) break

                    pendingTranscripts.removeAll(batch)
                    applyTranscripts(batch)
                }
            } finally {
                incrementalInFlight.set(false)
            }
        }
    }

    private fun applyTranscripts(paths: Set<Path>) {
        val basePath = project.basePath ?: return
        val current = cachedSessions ?: loadSessions().also { cachedSessions = it }
        val (present, gone) = paths.partition { Files.exists(it) }

        // A deleted transcript still has an accumulator holding everything it ever said, so
        // reparsing it would put the session straight back into the list.
        gone.forEach { accumulators.remove(it) }
        val removedIds = gone.mapTo(HashSet()) { it.fileName.toString().removeSuffix(".jsonl") }

        val reparsed = present.mapNotNull { path ->
            runCatching { parseJsonlSession(path, worktreeNameOf(path, basePath)) }.getOrNull()
        }
        if (reparsed.isEmpty() && removedIds.isEmpty()) return

        val byId = reparsed.associateBy { it.sessionId }
        cachedSessions = (current.filterNot { it.sessionId in byId || it.sessionId in removedIds } + reparsed)
            .sortedByDescending { it.lastPromptAt ?: it.modified }

        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it() }
        }
    }

    /** A transcript's directory says which worktree it belongs to; nothing else has to be read. */
    private fun worktreeNameOf(path: Path, basePath: String): String? {
        val directory = path.parent ?: return null

        return runCatching {
            ClaudePathEncoder.worktreeNames(basePath).firstOrNull {
                ClaudePathEncoder.worktreeProjectDir(basePath, it) == directory
            }
        }.getOrNull()
    }

    fun startWatching() {
        val basePath = project.basePath ?: return
        val projectDirs = ClaudePathEncoder.projectDirCandidates(basePath)

        project.messageBus.connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val projectDirStrs = projectDirs.map { it.toString() }
                    val sessionsDirStr = ClaudePathEncoder.sessionsDir().toString()
                    val transcripts = events.mapNotNullTo(HashSet()) { event ->
                        val path = event.path
                        if (!path.endsWith(".jsonl") || projectDirStrs.none { path.startsWith(it) }) {
                            return@mapNotNullTo null
                        }
                        Path.of(path)
                    }
                    val externals = events.any { event ->
                        (event is VFileContentChangeEvent || event is VFileCreateEvent) &&
                            event.path.startsWith(sessionsDirStr) &&
                            event.path.endsWith(".json")
                    }

                    if (transcripts.isNotEmpty()) refreshTranscripts(transcripts)
                    if (externals) refresh()
                }
            })

        val vfm = VirtualFileManager.getInstance()
        for (dir in projectDirs) vfm.refreshAndFindFileByNioPath(dir)
        vfm.refreshAndFindFileByNioPath(ClaudePathEncoder.sessionsDir())

        startPolling()
    }

    private fun startPolling() {
        // First check runs quickly so external session status is known before the first render
        pollAlarm.addRequest(::checkForChanges, 200)
    }

    private fun checkForChanges() {
        val basePath = project.basePath ?: return

        // Check main project dir(s) and all worktree project dirs
        val dirsToCheck = ClaudePathEncoder.projectDirCandidates(basePath).toMutableList()
        try {
            for (name in ClaudePathEncoder.worktreeNames(basePath)) {
                dirsToCheck.add(ClaudePathEncoder.worktreeProjectDir(basePath, name))
            }
        } catch (_: Exception) {}

        try {
            var latestMtime = 0L
            for (dir in dirsToCheck) {
                if (Files.isDirectory(dir)) {
                    Files.list(dir).use { stream ->
                        stream.filter { it.toString().endsWith(".jsonl") }
                            .forEach { path ->
                                val mtime = Files.getLastModifiedTime(path).toMillis()
                                if (mtime > latestMtime) latestMtime = mtime
                            }
                    }
                }
            }
            if (latestMtime != lastKnownMtime && latestMtime > 0) {
                lastKnownMtime = latestMtime
                refresh()
            }
        } catch (_: Exception) {
        }

        // Refresh external session detection
        val changed = refreshExternalSessions()
        if (changed) {
            ApplicationManager.getApplication().invokeLater {
                listeners.forEach { it() }
            }
        }

        if (!pollAlarm.isDisposed) {
            pollAlarm.addRequest(::checkForChanges, POLL_MS)
        }
    }

    /**
     * Returns true if external session state changed.
     *
     * Rate-limited: the detector scans the whole process table, and both the 5 s poll and
     * every list refresh want an answer. External terminals appear and disappear on human
     * timescales, so a floor of [EXTERNAL_SWEEP_MIN_MS] loses nothing.
     */
    private fun refreshExternalSessions(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastExternalSweep < EXTERNAL_SWEEP_MIN_MS) return false
        lastExternalSweep = now
        val ids = ClaudeProcessDetector.detectExternalSessions(project.basePath, cachedSessions ?: emptyList())
        val changed = ids != externalSessionIds
        externalSessionIds = ids
        return changed
    }

    private fun loadSessions(): List<SessionDisplay> {
        val basePath = project.basePath ?: run {
            log.warn("Canopy: project.basePath is null — cannot discover sessions")
            return emptyList()
        }
        val candidateDirs = ClaudePathEncoder.projectDirCandidates(basePath)
        val seen = java.util.HashSet<Path>()
        val mainSessions = candidateDirs.flatMap { loadSessionsFromDir(it, null, seen) }
        val wtSessions = try {
            ClaudePathEncoder.worktreeNames(basePath).flatMap { name ->
                val wtProjectDir = ClaudePathEncoder.worktreeProjectDir(basePath, name)
                loadSessionsFromDir(wtProjectDir, name, seen)
            }
        } catch (_: Exception) { emptyList() }
        // Drop parse state for transcripts that are gone (deleted sessions, removed worktrees)
        // so their accumulators don't outlive them.
        accumulators.keys.retainAll(seen)
        SessionDigestCache.getInstance(project).retainOnly(seen.map { it.toString() }.toSet())
        return (mainSessions + wtSessions).sortedByDescending { it.modified }
    }

    private fun loadSessionsFromDir(
        projectDir: Path,
        worktreeName: String?,
        seen: MutableSet<Path>
    ): List<SessionDisplay> {
        if (!Files.isDirectory(projectDir)) return emptyList()

        val sessions = mutableListOf<SessionDisplay>()
        try {
            Files.list(projectDir).use { stream ->
                stream.filter { it.toString().endsWith(".jsonl") }
                    .forEach { path ->
                        seen.add(path)
                        try {
                            val session = parseJsonlSession(path, worktreeName)
                            if (session != null) sessions.add(session)
                        } catch (e: Exception) {
                            log.warn("Failed to parse session file: $path", e)
                        }
                    }
            }
        } catch (e: Exception) {
            log.error("Failed to list project directory: $projectDir", e)
        }
        return sessions.sortedByDescending { it.modified }
    }

    /**
     * A transcript that has not changed since it was last parsed is never opened again: these files
     * only grow, and a project directory here reaches gigabytes.
     */
    private fun parseJsonlSession(path: Path, worktreeName: String?): SessionDisplay? {
        val sessionId = path.fileName.toString().removeSuffix(".jsonl")
        val digests = SessionDigestCache.getInstance(project)
        val size = try { Files.size(path) } catch (_: Exception) { -1L }
        val modifiedAt = try { Files.getLastModifiedTime(path).toMillis() } catch (_: Exception) { 0L }

        if (!accumulators.containsKey(path)) {
            digests.get(path.toString(), size, modifiedAt, project.basePath.orEmpty())?.let { return it }
        }

        val acc = accumulators.computeIfAbsent(path) { SessionAccumulator() }

        synchronized(acc) {
            try {
                acc.tail.consume(path, onRestart = { acc.reset() }) { line -> applyLine(acc, line) }
            } catch (e: Exception) {
                log.warn("Failed to read session file: $path", e)
            }

            val firstPrompt = acc.firstPrompt ?: return null

            // Use file modification time if no timestamp found in content
            val modified = if (acc.lastTimestamp == Instant.EPOCH) {
                Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis())
            } else {
                acc.lastTimestamp
            }

            val session = SessionDisplay(
                sessionId = sessionId,
                name = acc.customTitle,
                firstPrompt = firstPrompt,
                messageCount = acc.messageCount,
                modified = modified,
                gitBranch = acc.gitBranch,
                projectPath = project.basePath ?: "",
                worktreeName = worktreeName,
                touchedRoots = acc.rootsOfTouched(),
                startedAt = acc.firstTimestamp,
                tail = acc.turnTail,
                lastPromptAt = acc.lastPromptAt
            )
            SessionFileIndex.getInstance(project).record(sessionId, acc.touchedPaths)
            digests.put(path.toString(), size, modifiedAt, session)

            return session
        }
    }

    /** Fold one transcript line into [acc]. Every update is order-independent or last-wins. */
    private fun applyLine(acc: SessionAccumulator, line: String) {
        try {
            val obj = gson.fromJson(line, JsonObject::class.java)
            val type = obj.get("type")?.asString

            val text = com.canopy.util.leadingTextOf(obj.getAsJsonObject("message")).orEmpty()
            if (type == "user" || type == "assistant") {
                acc.messageCount++
                acc.turnTail = com.canopy.model.transcriptTailOf(
                    type = type,
                    isCompactSummary = obj.get("isCompactSummary")?.asBoolean == true,
                    isMeta = obj.get("isMeta")?.asBoolean == true,
                    hasToolUseResult = obj.has("toolUseResult"),
                    text = text
                ) ?: acc.turnTail
            }
            if (type == "user" && !obj.has("toolUseResult") && obj.get("isMeta")?.asBoolean != true) {
                acc.lastPromptLine = acc.messageCount
            }

            if (type == "assistant" && acc.touchedPaths.size < MAX_TOUCHED_PATHS) {
                acc.touchedPaths.addAll(com.canopy.util.touchedFilePathsIn(obj))
            }

            // /rename writes a custom-title entry; keep the last one
            if (type == "custom-title") {
                acc.customTitle = obj.get("customTitle")?.asString
            }

            if (type == "user" && acc.firstPrompt == null) {
                acc.firstPrompt = text.ifEmpty { null }?.let(::openingWords)
                acc.gitBranch = obj.get("gitBranch")?.asString
            }

            // Track latest timestamp
            val ts = obj.get("timestamp")
            if (ts != null) {
                val instant = when {
                    ts.isJsonPrimitive && ts.asJsonPrimitive.isNumber ->
                        Instant.ofEpochMilli(ts.asLong)
                    ts.isJsonPrimitive && ts.asJsonPrimitive.isString ->
                        parseInstant(ts.asString)
                    else -> null
                }
                if (instant != null && instant > acc.lastTimestamp) {
                    acc.lastTimestamp = instant
                }
                if (instant != null && acc.lastPromptLine == acc.messageCount) {
                    acc.lastPromptAt = instant
                }
                if (instant != null && (acc.firstTimestamp == null || instant < acc.firstTimestamp)) {
                    acc.firstTimestamp = instant
                }
            }
        } catch (_: Exception) {
            // Skip malformed lines
        }
    }

    private fun parseInstant(dateStr: String): Instant {
        return try {
            Instant.parse(dateStr)
        } catch (_: Exception) {
            Instant.EPOCH
        }
    }


    override fun dispose() {
        listeners.clear()
    }

    companion object {
        /** Floor between `ps`-based external-session sweeps. */
        private const val EXTERNAL_SWEEP_MIN_MS = 4_000L

        fun getInstance(project: Project): ClaudeSessionService =
            project.getService(ClaudeSessionService::class.java)
    }
}

private const val POLL_MS = 5_000
