package com.canopy.toolwindow

import com.canopy.settings.CanopySettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object WorktreeProvisioning {

    private val log = Logger.getInstance(WorktreeProvisioning::class.java)

    fun provision(project: Project, repoKey: String, sourceRoot: String, targetPath: String): List<String> {
        val settings = CanopySettings.getInstance().state
        val files = setupFilesFor(settings.worktreeSetupFilesByRepo, repoKey, settings.worktreeSetupFiles)
        val command = setupCommandFor(settings.worktreeSetupCommandByRepo, repoKey, settings.worktreeSetupCommand)
        val copied = copyFiles(sourceRoot, targetPath, setupFileNames(files))
        runSetupCommand(project, targetPath, command.trim())

        return copied
    }

    private fun copyFiles(sourceRoot: String, targetPath: String, names: List<String>): List<String> {
        val source = Path.of(sourceRoot)
        val target = Path.of(targetPath)
        val pending = filesToProvision(
            names,
            existsInSource = { Files.exists(source.resolve(it)) },
            existsInTarget = { Files.exists(target.resolve(it)) }
        )

        return pending.filter { name -> copyOne(source.resolve(name), target.resolve(name)) }
    }

    private fun copyOne(from: Path, to: Path): Boolean =
        try {
            to.parent?.let { Files.createDirectories(it) }
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES)
            true
        } catch (e: Exception) {
            log.warn("Canopy: failed to provision $from into $to", e)
            false
        }

    private fun runSetupCommand(project: Project, targetPath: String, command: String) {
        if (command.isEmpty()) return

        try {
            val tabState = TerminalTabState().apply {
                myTabName = "setup: ${Path.of(targetPath).fileName}"
                myWorkingDirectory = targetPath
                myShellCommand = listOf(System.getenv("SHELL") ?: "/bin/zsh", "-lc", command)
            }
            TerminalToolWindowManager.getInstance(project)
                .createNewSession(LocalTerminalDirectRunner.createTerminalRunner(project), tabState)
        } catch (e: Exception) {
            log.warn("Canopy: worktree setup command failed to start in $targetPath", e)
        }
    }
}

internal fun setupFileNames(configured: String): List<String> =
    configured.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

internal fun filesToProvision(
    names: List<String>,
    existsInSource: (String) -> Boolean,
    existsInTarget: (String) -> Boolean
): List<String> = names.filter { existsInSource(it) && !existsInTarget(it) }
