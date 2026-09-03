package com.canopy.util

import com.canopy.model.RepoScope
import java.nio.file.Path

object RepoScopeDiscovery {

    private const val GIT_TIMEOUT_MS = 15_000L
    private const val SHA_LENGTH = 40
    private const val UNINITIALIZED_MARKER = '-'

    fun discover(projectBasePath: String): List<RepoScope> {
        val superproject = scopeAt(projectBasePath, isSubmodule = false) ?: return emptyList()

        val submodules = submodulePaths(projectBasePath)
            .mapNotNull { scopeAt(it, isSubmodule = true) }
            .sortedBy { it.label.lowercase() }

        return listOf(superproject) + submodules
    }

    /**
     * Git resolves upward, so an uninitialized submodule directory answers for its superproject
     * instead of failing. Matching the toplevel against [path] is what rejects those.
     */
    private fun scopeAt(path: String, isSubmodule: Boolean): RepoScope? {
        val toplevel = git(path, "rev-parse", "--show-toplevel")?.trim() ?: return null
        if (!isSamePath(toplevel, path)) return null

        val commonDir = git(path, "rev-parse", "--git-common-dir")?.trim() ?: return null

        return RepoScope(
            label = Path.of(toplevel).fileName?.toString() ?: toplevel,
            root = toplevel,
            gitCommonDir = absolutize(commonDir, toplevel),
            isSubmodule = isSubmodule
        )
    }

    private fun submodulePaths(projectBasePath: String): List<String> {
        val output = git(projectBasePath, "submodule", "status", "--recursive") ?: return emptyList()

        return initializedSubmodulePaths(output)
            .map { Path.of(projectBasePath, it).toAbsolutePath().toString() }
    }

    internal fun initializedSubmodulePaths(statusOutput: String): List<String> =
        statusOutput.lines()
            .filter { it.isNotBlank() }
            .filterNot { it.first() == UNINITIALIZED_MARKER }
            .mapNotNull { parseSubmodulePath(it) }

    /** A status line is `<state><sha> <path>` with an optional ` (<describe>)` suffix. */
    internal fun parseSubmodulePath(line: String): String? {
        val afterSha = line.drop(1 + SHA_LENGTH).trim()
        if (afterSha.isEmpty()) return null
        if (!afterSha.endsWith(")")) return afterSha

        val describeStart = afterSha.lastIndexOf(" (")
        return if (describeStart <= 0) afterSha else afterSha.take(describeStart)
    }

    private fun absolutize(candidate: String, base: String): String =
        Path.of(base).resolve(candidate).normalize().toAbsolutePath().toString()

    private fun git(workingDir: String, vararg args: String): String? {
        return try {
            val result = ProcessHelper.execWithTimeout(
                command = arrayOf("git", "-C", workingDir, *args),
                timeoutMs = GIT_TIMEOUT_MS,
                extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
            )
            if (result.exitCode == 0) result.output else null
        } catch (_: Exception) {
            null
        }
    }
}
