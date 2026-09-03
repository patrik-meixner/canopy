package com.canopy.toolwindow

import com.canopy.model.OrphanReason
import com.canopy.model.RepoScope
import com.canopy.model.RepoWorktrees
import com.canopy.util.ClaudePathEncoder
import com.canopy.util.ProcessHelper
import com.canopy.util.isSamePath
import java.nio.file.Files
import java.nio.file.Path

data class WorktreeEntry(
    val path: String,
    val branch: String?,
    val prunable: Boolean,
    val prunableReason: String?
)

data class OrphanWorktree(
    val name: String,
    val path: String,
    val branch: String?,
    val reason: OrphanReason,
    val repoLabel: String,
    val repoRoot: String
) {
    val registered: Boolean get() = reason is OrphanReason.NoSession
}

enum class WorktreeState { OK, REBASING, MERGING, CHERRY_PICKING, DETACHED, MISSING, UNKNOWN }

/** [ahead] is reachability, so a branch merged by squashing still reads as ahead of [mainBranch]. */
data class WorktreeStatus(
    val state: WorktreeState,
    val name: String,
    val wtBranch: String? = null,
    val mainBranch: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val dirtyFiles: List<String> = emptyList(),
    val errorDetail: String? = null
) {
    val dirtyCount: Int get() = dirtyFiles.size
    val dirty: Boolean get() = dirtyFiles.isNotEmpty()
}

enum class BaseBranchState { SYNCED, LOCAL_AHEAD, LOCAL_BEHIND, LOCAL_DIVERGED, OTHER_BRANCH, NO_REMOTE_DEFAULT, NO_LOCAL_DEFAULT, UNKNOWN }

data class BaseBranchInfo(
    val state: BaseBranchState,
    val currentBranch: String? = null,
    val defaultBranch: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val errorDetail: String? = null
)

object WorktreeInspector {

    private const val LEFTOVER_SAMPLE_SIZE = 4L

    private val WORKTREE_DIRECTORY_NAMES = listOf(arrayOf(".claude", "worktrees"), arrayOf(".worktrees"))

    fun normalize(name: String): String = name.replace("/", "+")

    fun list(projectDir: String): List<WorktreeEntry> {
        return try {
            val res = ProcessHelper.execWithTimeout(
                command = arrayOf("git", "-C", projectDir, "worktree", "list", "--porcelain"),
                timeoutMs = 15_000,
                extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
            )
            parse(res.output)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun collect(scope: RepoScope): RepoWorktrees {
        val entries = list(scope.root)

        return RepoWorktrees(scope, mainBranchOf(entries, scope), secondaryWorktrees(entries, scope))
    }

    internal fun mainBranchOf(entries: List<WorktreeEntry>, scope: RepoScope): String? =
        entries.firstOrNull { isMainWorktree(it.path, scope) }?.branch

    /**
     * Git reports a submodule's main worktree as the shared git directory rather than as the
     * checkout, so dropping the root alone would leave that phantom entry in the list.
     */
    internal fun secondaryWorktrees(entries: List<WorktreeEntry>, scope: RepoScope): List<WorktreeEntry> =
        entries.filterNot { isMainWorktree(it.path, scope) }

    private fun isMainWorktree(path: String, scope: RepoScope): Boolean =
        isSamePath(path, scope.root) || isSamePath(path, scope.gitCommonDir)

    fun prune(projectDir: String): Pair<Boolean, String> {
        return try {
            val res = ProcessHelper.execWithTimeout(
                command = arrayOf("git", "-C", projectDir, "worktree", "prune"),
                timeoutMs = 15_000,
                extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
            )
            (res.exitCode == 0) to res.output
        } catch (e: Exception) {
            false to (e.message ?: "exception")
        }
    }

    fun findOrphans(scopes: List<RepoScope>, sessionWorktreeNames: Set<String>): List<OrphanWorktree> =
        scopes.flatMap { orphansIn(it, sessionWorktreeNames) }
            .sortedWith(compareBy({ it.repoLabel.lowercase() }, { it.name.lowercase() }))

    private fun orphansIn(scope: RepoScope, sessionWorktreeNames: Set<String>): List<OrphanWorktree> {
        val registered = try {
            secondaryWorktrees(list(scope.root), scope)
        } catch (_: Exception) {
            return emptyList()
        }
        val registeredPaths = registered.map { it.path }.toSet()

        val tracked = registered.map { entry ->
            OrphanWorktree(
                name = worktreeNameOf(entry.path),
                path = entry.path,
                branch = entry.branch,
                reason = OrphanReason.NoSession,
                repoLabel = scope.label,
                repoRoot = scope.root
            )
        }
        val leftovers = leftoverDirectories(scope, registeredPaths).map { path ->
            OrphanWorktree(
                name = worktreeNameOf(path),
                path = path,
                branch = null,
                reason = diagnose(path, scope),
                repoLabel = scope.label,
                repoRoot = scope.root
            )
        }

        return (tracked + leftovers).filter { it.name !in sessionWorktreeNames }
    }

    private fun leftoverDirectories(scope: RepoScope, registeredPaths: Set<String>): List<String> =
        WORKTREE_DIRECTORY_NAMES
            .map { Path.of(scope.root, *it) }
            .filter { Files.isDirectory(it) }
            .flatMap { directory ->
                Files.list(directory).use { stream ->
                    stream.filter { Files.isDirectory(it) }.map { it.toAbsolutePath().toString() }.toList()
                }
            }
            .filterNot { candidate -> registeredPaths.any { isSamePath(it, candidate) } }

    private fun diagnose(path: String, scope: RepoScope): OrphanReason {
        val directory = Path.of(path)
        val gitLink = directory.resolve(".git")

        if (Files.isRegularFile(gitLink)) {
            val gitDir = Files.readString(gitLink).substringAfter("gitdir:").trim()
            val resolved = directory.resolve(gitDir).normalize()
            if (!Files.exists(resolved)) return OrphanReason.MissingGitDir(resolved.toString())
            if (!isSamePath(resolved.parent?.parent?.toString().orEmpty(), scope.gitCommonDir)) {
                return OrphanReason.OwnedByOtherRepo(resolved.toString())
            }
        }

        val names = Files.list(directory).use { stream ->
            stream.limit(LEFTOVER_SAMPLE_SIZE).map { it.fileName.toString() }.toList()
        }

        return if (names.isEmpty()) OrphanReason.EmptyDirectory else OrphanReason.LeftoverFiles(names)
    }

    private fun worktreeNameOf(path: String): String =
        Path.of(path).fileName?.toString() ?: path

    fun remove(projectDir: String, worktreePath: String, force: Boolean): Pair<Boolean, String> {
        return try {
            val cmd = if (force) {
                arrayOf("git", "-C", projectDir, "worktree", "remove", "--force", worktreePath)
            } else {
                arrayOf("git", "-C", projectDir, "worktree", "remove", worktreePath)
            }
            val res = ProcessHelper.execWithTimeout(
                command = cmd,
                timeoutMs = 15_000,
                extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
            )
            (res.exitCode == 0) to res.output
        } catch (e: Exception) {
            false to (e.message ?: "exception")
        }
    }

    fun deleteBranchIfMerged(projectDir: String, branch: String): Pair<Boolean, String> {
        return try {
            val res = ProcessHelper.execWithTimeout(
                command = arrayOf("git", "-C", projectDir, "branch", "-d", branch),
                timeoutMs = 15_000,
                extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
            )
            (res.exitCode == 0) to res.output
        } catch (e: Exception) {
            false to (e.message ?: "exception")
        }
    }

    fun deleteDirectory(path: String): Pair<Boolean, String> {
        return try {
            val dir = Path.of(path)
            if (Files.exists(dir)) {
                Files.walk(dir).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
            true to ""
        } catch (e: Exception) {
            false to (e.message ?: "exception")
        }
    }

    fun inspectBaseBranch(projectDir: String): BaseBranchInfo {
        return try {
            // Prefer origin/HEAD (set on fresh clones), but many repos don't have it
            // configured even when origin/main clearly exists — fall back to probing.
            val defaultBranch = run {
                val (rc, out) = runGit(projectDir, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
                val fromHead = if (rc == 0) out.trim().removePrefix("origin/").ifEmpty { null } else null
                fromHead
                    ?: listOf("main", "master").firstOrNull { name ->
                        runGit(projectDir, "rev-parse", "--verify", "--quiet", "refs/remotes/origin/$name").first == 0
                    }
                    ?: return BaseBranchInfo(
                        state = BaseBranchState.NO_REMOTE_DEFAULT,
                        errorDetail = "No origin/HEAD, origin/main, or origin/master found"
                    )
            }

            val (curRc, curOut) = runGit(projectDir, "branch", "--show-current")
            val current = if (curRc == 0) curOut.trim() else ""

            val (localRc, _) = runGit(projectDir, "rev-parse", "--verify", "--quiet", "refs/heads/$defaultBranch")
            if (localRc != 0) {
                return BaseBranchInfo(
                    state = BaseBranchState.NO_LOCAL_DEFAULT,
                    currentBranch = current.ifEmpty { null },
                    defaultBranch = defaultBranch
                )
            }

            if (current != defaultBranch) {
                return BaseBranchInfo(
                    state = BaseBranchState.OTHER_BRANCH,
                    currentBranch = current.ifEmpty { null },
                    defaultBranch = defaultBranch
                )
            }

            val (cntRc, cntOut) = runGit(projectDir, "rev-list", "--left-right", "--count", "refs/remotes/origin/$defaultBranch...refs/heads/$defaultBranch")
            val parts = if (cntRc == 0) cntOut.trim().split("\t") else emptyList()
            val behind = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val ahead = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val state = when {
                ahead > 0 && behind > 0 -> BaseBranchState.LOCAL_DIVERGED
                ahead > 0 -> BaseBranchState.LOCAL_AHEAD
                behind > 0 -> BaseBranchState.LOCAL_BEHIND
                else -> BaseBranchState.SYNCED
            }
            BaseBranchInfo(
                state = state,
                currentBranch = current,
                defaultBranch = defaultBranch,
                ahead = ahead,
                behind = behind
            )
        } catch (e: Exception) {
            BaseBranchInfo(state = BaseBranchState.UNKNOWN, errorDetail = e.message ?: e.javaClass.simpleName)
        }
    }

    fun currentBranch(repositoryPath: String): String? = runGit(repositoryPath, "branch", "--show-current")
        .takeIf { it.first == 0 }
        ?.second
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun status(worktreePath: String, projectPath: String, knownMainBranch: String? = null): WorktreeStatus {
        val name = worktreePath.substringAfterLast('/')

        val wtPath = Path.of(worktreePath)
        if (!Files.isDirectory(wtPath)) {
            return WorktreeStatus(WorktreeState.MISSING, name, errorDetail = "Worktree directory not found at $worktreePath")
        }

        try {
            val gitDir = runGit(worktreePath, "rev-parse", "--git-dir")
                .takeIf { it.first == 0 }
                ?.second
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { wtPath.resolve(it) }

            fun inGitDir(entry: String): Boolean = gitDir != null && Files.exists(gitDir.resolve(entry))

            val inProgress = when {
                inGitDir("rebase-merge") || inGitDir("rebase-apply") -> WorktreeState.REBASING
                inGitDir("MERGE_HEAD") -> WorktreeState.MERGING
                inGitDir("CHERRY_PICK_HEAD") -> WorktreeState.CHERRY_PICKING
                else -> null
            }

            val (wtRc, wtOut) = runGit(worktreePath, "branch", "--show-current")
            if (wtRc != 0) {
                return WorktreeStatus(
                    state = WorktreeState.UNKNOWN,
                    name = name,
                    errorDetail = wtOut.trim().take(200).ifEmpty { "git branch --show-current failed" }
                )
            }
            val wtBranch = wtOut.trim()

            val mainBranch = knownMainBranch ?: runGit(projectPath, "branch", "--show-current")
                .takeIf { it.first == 0 }
                ?.second
                ?.trim()
                .orEmpty()

            val (dirtyRc, dirtyOut) = runGit(worktreePath, "status", "--porcelain")
            // Porcelain v1: "XY <path>" — the path starts at column 3. Renames read
            // "R  old -> new"; keep the whole payload, it reads fine in a tooltip.
            val dirtyFiles = if (dirtyRc == 0) {
                dirtyOut.lines().filter { it.isNotBlank() }.map { it.drop(3).trim() }
            } else {
                emptyList()
            }

            if (inProgress != null) {
                return WorktreeStatus(
                    state = inProgress,
                    name = name,
                    wtBranch = wtBranch.ifEmpty { null },
                    mainBranch = mainBranch.ifEmpty { null },
                    dirtyFiles = dirtyFiles
                )
            }

            if (wtBranch.isEmpty() || mainBranch.isEmpty()) {
                return WorktreeStatus(
                    state = WorktreeState.DETACHED,
                    name = name,
                    wtBranch = wtBranch.ifEmpty { null },
                    mainBranch = mainBranch.ifEmpty { null },
                    dirtyFiles = dirtyFiles
                )
            }

            val (cntRc, cntOut) = runGit(worktreePath, "rev-list", "--left-right", "--count", "$mainBranch...$wtBranch")
            val parts = if (cntRc == 0) cntOut.trim().split("\t") else emptyList()
            val behind = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val ahead = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0

            return WorktreeStatus(
                state = WorktreeState.OK,
                name = name,
                wtBranch = wtBranch,
                mainBranch = mainBranch,
                ahead = ahead,
                behind = behind,
                dirtyFiles = dirtyFiles
            )
        } catch (e: Exception) {
            return WorktreeStatus(
                state = WorktreeState.UNKNOWN,
                name = name,
                errorDetail = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun runGit(projectDir: String, vararg args: String): Pair<Int, String> {
        val res = ProcessHelper.execWithTimeout(
            command = arrayOf("git", "-C", projectDir, *args),
            timeoutMs = 15_000,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        return res.exitCode to res.output
    }

    fun parse(output: String): List<WorktreeEntry> {
        val entries = mutableListOf<WorktreeEntry>()
        var path: String? = null
        var branch: String? = null
        var prunable = false
        var prunableReason: String? = null
        fun flush() {
            path?.let { entries.add(WorktreeEntry(it, branch, prunable, prunableReason)) }
            path = null; branch = null; prunable = false; prunableReason = null
        }
        for (line in output.lines()) {
            if (line.isBlank()) { flush(); continue }
            val parts = line.split(' ', limit = 2)
            when (parts[0]) {
                "worktree" -> path = parts.getOrNull(1)
                "branch" -> branch = parts.getOrNull(1)?.removePrefix("refs/heads/")
                "prunable" -> { prunable = true; prunableReason = parts.getOrNull(1) }
            }
        }
        flush()
        return entries
    }
}
