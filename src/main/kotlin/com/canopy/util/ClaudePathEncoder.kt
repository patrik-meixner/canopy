package com.canopy.util

import java.nio.file.Files
import java.nio.file.Path

object ClaudePathEncoder {

    fun encode(absolutePath: String): String = absolutePath.replace('/', '-')

    fun projectDir(projectBasePath: String): Path {
        val claudeHome = Path.of(System.getProperty("user.home"), ".claude")
        return claudeHome.resolve("projects").resolve(encode(projectBasePath))
    }

    /** Claude Code's encoder replaces `/` and `.`; [encode] replaces only `/`, so a dotted path has two homes. */
    fun projectDirCandidates(projectBasePath: String): List<Path> {
        val primary = projectDir(projectBasePath)
        if (!projectBasePath.contains('.')) return listOf(primary)
        val claudeHome = Path.of(System.getProperty("user.home"), ".claude")
        val altEncoded = projectBasePath.replace('/', '-').replace('.', '-')
        val alt = claudeHome.resolve("projects").resolve(altEncoded)
        return if (alt == primary) listOf(primary) else listOf(primary, alt)
    }

    fun tasksDir(): Path = Path.of(System.getProperty("user.home"), ".claude", "tasks")

    fun sessionsDir(): Path =
        Path.of(System.getProperty("user.home"), ".claude", "sessions")

    fun tempRoot(): Path {
        val base = System.getenv("CLAUDE_CODE_TMPDIR")?.takeIf { it.isNotBlank() } ?: "/tmp"
        return Path.of(base, "claude-$currentUid")
    }

    /** POSIX uid via NIO. Claude Code falls back to 0 where `getuid` is absent (Windows); match that. */
    private val currentUid: Int by lazy {
        try {
            Files.getAttribute(Path.of(System.getProperty("user.home")), "unix:uid") as? Int ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun scratchpadCandidates(sessionCwd: String, sessionId: String): List<Path> {
        val root = tempRoot()
        val fullyEncoded = sessionCwd.replace('/', '-').replace('.', '-')
        return linkedSetOf(fullyEncoded, encode(sessionCwd))
            .map { root.resolve(it).resolve(sessionId).resolve("scratchpad") }
    }

    fun worktreeNames(projectBasePath: String): List<String> {
        val wtDir = Path.of(projectBasePath, ".claude", "worktrees")
        if (!Files.isDirectory(wtDir)) return emptyList()
        return Files.list(wtDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .toList()
        }
    }

    fun worktreeAbsolutePath(projectBasePath: String, worktreeName: String): String =
        Path.of(projectBasePath, ".claude", "worktrees", worktreeName).toAbsolutePath().toString()

    /** A worktree path always carries a dot, so it takes Claude Code's full encoding rather than [encode]. */
    fun worktreeProjectDir(projectBasePath: String, worktreeName: String): Path {
        val claudeHome = Path.of(System.getProperty("user.home"), ".claude")
        val wtPath = worktreeAbsolutePath(projectBasePath, worktreeName)
        val encoded = wtPath.replace('/', '-').replace('.', '-')
        return claudeHome.resolve("projects").resolve(encoded)
    }
}
