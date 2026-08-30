package com.canopy.services

import com.canopy.terminal.ActivityMonitoringTtyConnector
import com.canopy.terminal.ClipboardImagePaste
import com.canopy.terminal.FilteringPtyConnector
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.JBTerminalWidget
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class TerminalSession(val widget: JBTerminalWidget, val process: PtyProcess)

@Service(Service.Level.PROJECT)
class ClaudeTerminalService(private val project: Project) {

    private val log = Logger.getInstance(ClaudeTerminalService::class.java)

    fun createResumeWidget(
        sessionId: String,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(
            arrayOf("claude", "--resume", sessionId),
            parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive        )
    }

    fun createForkWidget(
        forkFromSessionId: String,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(
            arrayOf("claude", "--resume", forkFromSessionId, "--fork-session"),
            parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive        )
    }

    fun createNewWorktreeWidget(
        worktreeName: String,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(
            arrayOf("claude", "--worktree", worktreeName, "--name", worktreeName),
            parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive        )
    }

    fun createNewNamedSessionWidget(
        name: String,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(arrayOf("claude", "--name", name), parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive)
    }

    /**
     * A plain login shell rather than a Claude session, so the agent and account can be chosen
     * by hand. Login shells pick up the user's own PATH, which is where wrappers like `claude-p` live.
     */
    fun createShellWidget(parent: Disposable, workingDir: String? = null): TerminalSession {
        val shell = com.canopy.util.ProcessHelper.augmentedEnv()["SHELL"] ?: "/bin/zsh"

        return createWidget(arrayOf(shell, "-l"), parent, workingDir = workingDir, isShell = true)
    }

    private fun createWidget(
        command: Array<String>,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onReady: ((PtyProcess) -> Unit)? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null,
        isShell: Boolean = false
    ): TerminalSession {
        val env = com.canopy.util.ProcessHelper.augmentedEnv()
        env["TERM"] = "xterm-256color"
        // Claude Code gates OSC 8 hyperlinks on this exact value, as does IntelliJ's own terminal runner.
        env["TERMINAL_EMULATOR"] = "JetBrains-JediTerm"
        val settings = com.canopy.settings.CanopySettings.getInstance()
        val envOverrides = settings.environmentOverrides()
        env.putAll(envOverrides)
        if (envOverrides.isNotEmpty()) {
            log.info("Canopy: createWidget — env overrides: $envOverrides")
        }

        val effectiveWorkingDir = workingDir ?: project.basePath ?: System.getProperty("user.home")
        log.info("Canopy: createWidget — command=${command.toList()}, workingDir=$effectiveWorkingDir")
        val resolvedCommand = if (isShell) {
            command
        } else {
            val claudePath = settings.resolveClaudeBinary()
            log.info("Canopy: createWidget — '${command[0]}' resolved to: $claudePath")
            arrayOf(claudePath) + command.drop(1).toTypedArray() + settings.extraSessionArgs().toTypedArray()
        }

        // Build the full command, adding --settings override for status interception
        val fullCommand = if (statusFile != null) {
            val statusService = ClaudeStatusService.getInstance(project)
            val wrapperPath = statusService.getWrapperScriptPath()
            val notifyScriptPath = statusService.getNotifyScriptPath()
            val overridePath = statusService.createOverrideSettingsFile(wrapperPath, notifyScriptPath)

            env["CANOPY_STATUS_FILE"] = statusFile.toAbsolutePath().toString()
            env["CANOPY_NOTIFY_FILE"] = notifyFile?.toAbsolutePath()?.toString() ?: ""
            env["CANOPY_ORIGINAL_STATUSLINE"] = statusService.discoverOriginalStatusLineCommand() ?: ""

            Disposer.register(parent, Disposable {
                try { Files.deleteIfExists(overridePath) } catch (_: Exception) {}
            })

            // Insert --settings right after the binary
            arrayOf(resolvedCommand[0], "--settings", overridePath.toAbsolutePath().toString()) +
                resolvedCommand.drop(1).toTypedArray()
        } else {
            resolvedCommand
        }

        log.info("Canopy: createWidget — fullCommand=${fullCommand.toList()}, PATH=${env["PATH"]?.take(200)}")

        val ptyProcess = try {
            PtyProcessBuilder(fullCommand)
                .setEnvironment(env)
                .setDirectory(effectiveWorkingDir)
                .start()
        } catch (e: Exception) {
            log.error("Canopy: createWidget — failed to start PTY process: ${fullCommand.toList()}", e)
            throw e
        }

        // Wrap onActiveChanged to fire onReady on the first idle transition
        val wrappedOnActiveChanged: ((Boolean) -> Unit)? = if (onActiveChanged != null || onReady != null) {
            var readyFired = false
            { active: Boolean ->
                if (!active && !readyFired && onReady != null) {
                    readyFired = true
                    onReady(ptyProcess)
                }
                onActiveChanged?.invoke(active)
            }
        } else null

        val connector = if (wrappedOnActiveChanged != null) {
            val echoTimeout = com.canopy.settings.CanopySettings.getInstance().state.echoTimeoutMs.toLong()
            ActivityMonitoringTtyConnector(ptyProcess, StandardCharsets.UTF_8, echoTimeoutMs = echoTimeout, onActiveChanged = wrappedOnActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive)
        } else {
            FilteringPtyConnector(ptyProcess, StandardCharsets.UTF_8)
        }

        val settingsProvider = JBTerminalSystemSettingsProvider()
        val widget = JBTerminalWidget(project, settingsProvider, parent)
        widget.start(connector)
        ClipboardImagePaste.install(widget.terminalPanel) { sendInput(ptyProcess, it) }

        Disposer.register(parent, Disposable {
            if (ptyProcess.isAlive) {
                ptyProcess.destroy()
                // Force kill if it doesn't exit promptly (e.g. during tab detach)
                if (!ptyProcess.waitFor(2, TimeUnit.SECONDS)) {
                    ptyProcess.destroyForcibly()
                }
            }
        })

        return TerminalSession(widget, ptyProcess)
    }

    private fun sendInput(process: PtyProcess, text: String) {
        if (!process.isAlive) return
        try {
            process.outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
            process.outputStream.flush()
        } catch (e: Exception) {
            log.warn("Failed to send input to claude process", e)
        }
    }

    companion object {
        fun getInstance(project: Project): ClaudeTerminalService =
            project.getService(ClaudeTerminalService::class.java)
    }
}
