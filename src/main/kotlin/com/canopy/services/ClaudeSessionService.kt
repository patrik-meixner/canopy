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

    private val accumulators = java.util.concurrent.ConcurrentHashMap<Path, SessionAccumulator>()

    private val MAX_TOUCHED_PATHS = 2_000

    private class SessionAccumulator {
        val tail = com.canopy.util.JsonlTailReader()
        var firstPrompt: String? = null
        var customTitle: String? = null
        var messageCount = 0
        var gitBranch: String? = null
        var workingDirectory: String? = null
        var lastTimestamp: Instant = Instant.EPOCH
        var firstTimestamp: Instant? = null
        var turnTail: com.canopy.model.TranscriptTail? = null
        var lastPromptLine = 0
        var lastPromptAt: Instant? = null
        val touchedPaths = LinkedHashSet<String>()

        var roots: Map<String, Int> = emptyMap()
        var rootsFor = -1

        fun rootsOfTouched(): Map<String, Int> {
            if (rootsFor == touchedPaths.size) return roots

            rootsFor = touchedPaths.size
            roots = com.canopy.util.gitWorkingTreeRoots(touchedPaths)

            return roots
        }

        fun reset() {
            firstPrompt = null
            customTitle = null
            messageCount = 0
            gitBranch = null
            workingDirectory = null
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

    @Volatile
    private var externalSessionIds: Set<String> = emptySet()

    private val refreshInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val refreshQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var lastExternalSweep = 0L

    fun isExternallyOpen(sessionId: String): Boolean =
        sessionId in externalSessionIds

    @Volatile private var lastRefreshAt = 0L

    private fun refreshAfterFloor() {
        val waited = System.currentTimeMillis() - lastRefreshAt
        if (waited >= REFRESH_FLOOR_MS) return refresh()

        com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(::refresh, REFRESH_FLOOR_MS - waited, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun cachedDisplayName(sessionId: String): String? =
        cachedSessions?.firstOrNull { it.sessionId == sessionId }?.displayName

    fun cachedWorkingDir(sessionId: String): String? =
        cachedSessions?.firstOrNull { it.sessionId == sessionId }
            ?.let { session -> session.worktreeName?.let { com.canopy.util.ClaudePathEncoder.worktreeAbsolutePath(project.basePath ?: return null, it) } ?: session.projectPath }

    fun cachedStartedAt(sessionId: String): java.time.Instant? =
        cachedSessions?.firstOrNull { it.sessionId == sessionId }?.startedAt

    fun getSessions(): List<SessionDisplay> {
        cachedSessions?.let { return it }
        if (ApplicationManager.getApplication().isDispatchThread) {
            refresh()

            return emptyList()
        }

        return loadSessions().also { cachedSessions = it }
    }

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

        cachedSessions = cachedSessions?.filter { it.sessionId != sessionId }
        SessionDigestCache.getInstance(project).retainOnly(
            cachedSessions.orEmpty().mapNotNull { transcriptPathOf(it, basePath)?.toString() }.toSet()
        )
        notifyListeners()

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

    fun refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) {
            refreshQueued.set(true)
            return
        }
        CanopyExecutor.submit {
            try {
                val fresh = loadSessions()
                val sessionsChanged = fresh != cachedSessions
                cachedSessions = fresh
                val externalsChanged = refreshExternalSessions()
                if (sessionsChanged || externalsChanged) notifyListeners()
            } finally {
                lastRefreshAt = System.currentTimeMillis()
                refreshInFlight.set(false)
                if (refreshQueued.compareAndSet(true, false)) refreshAfterFloor()
            }
        }
    }

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

        gone.forEach { accumulators.remove(it) }
        val removedIds = gone.mapTo(HashSet()) { it.fileName.toString().removeSuffix(".jsonl") }

        val reparsed = present.mapNotNull { path ->
            runCatching { parseJsonlSession(path, worktreeNameOf(path, basePath)) }.getOrNull()
        }
        if (reparsed.isEmpty() && removedIds.isEmpty()) return

        val byId = reparsed.associateBy { it.sessionId }
        val updated = (current.filterNot { it.sessionId in byId || it.sessionId in removedIds } + reparsed)
            .sortedByDescending { it.lastPromptAt ?: it.modified }
        if (updated == current) return

        cachedSessions = updated
        notifyListeners()
    }

    private fun notifyListeners() {
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it() }
        }
    }

    private fun worktreeNameOf(path: Path, basePath: String): String? =
        worktreesByProjectDir(basePath)[path.parent]

    @Volatile private var worktreeDirs: Map<Path, String> = emptyMap()
    @Volatile private var worktreeDirsAt = 0L

    private fun worktreesByProjectDir(basePath: String): Map<Path, String> {
        val now = System.currentTimeMillis()
        if (now - worktreeDirsAt < WORKTREE_MAP_TTL_MS) return worktreeDirs

        worktreeDirsAt = now
        worktreeDirs = runCatching {
            ClaudePathEncoder.worktreeNames(basePath)
                .associateBy { ClaudePathEncoder.worktreeProjectDir(basePath, it) }
        }.getOrDefault(emptyMap())

        return worktreeDirs
    }

    fun startWatching() {
        val basePath = project.basePath ?: return
        val projectDirs = ClaudePathEncoder.projectDirCandidates(basePath)

        project.messageBus.connect(this)
            .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                private val projectDirStrs = projectDirs.map { it.toString() }
                private val sessionsDirStr = ClaudePathEncoder.sessionsDir().toString()

                override fun after(events: MutableList<out VFileEvent>) {
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
        pollAlarm.addRequest(::checkForChanges, 200)
    }

    private fun checkForChanges() {
        val basePath = project.basePath ?: return

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

        if (refreshExternalSessions()) notifyListeners()

        if (!pollAlarm.isDisposed) {
            pollAlarm.addRequest(::checkForChanges, POLL_MS)
        }
    }

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
                workingDirectory = acc.workingDirectory,
                startedAt = acc.firstTimestamp,
                tail = acc.turnTail,
                lastPromptAt = acc.lastPromptAt
            )
            SessionFileIndex.getInstance(project).record(sessionId, acc.touchedPaths)
            digests.put(path.toString(), size, modifiedAt, session)

            return session
        }
    }

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

            if (type == "custom-title") {
                acc.customTitle = obj.get("customTitle")?.asString
            }

            if (type == "user" && acc.firstPrompt == null) {
                acc.firstPrompt = text.ifEmpty { null }?.let(::openingWords)
                acc.gitBranch = obj.get("gitBranch")?.asString
            obj.get("cwd")?.asString?.let { acc.workingDirectory = it }
            }

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

        private const val REFRESH_FLOOR_MS = 750L
        private const val EXTERNAL_SWEEP_MIN_MS = 4_000L

        fun getInstance(project: Project): ClaudeSessionService =
            project.getService(ClaudeSessionService::class.java)
    }
}

private const val POLL_MS = 5_000
private const val WORKTREE_MAP_TTL_MS = 2_000L
