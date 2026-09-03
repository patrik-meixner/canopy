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
        onUnresponsive: (() -> Unit)? = null,
        onResponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(
            arrayOf("claude", "--resume", sessionId),
            parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive, onResponsive = onResponsive        )
    }

    fun createForkWidget(
        forkFromSessionId: String,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null,
        onResponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(
            arrayOf("claude", "--resume", forkFromSessionId, "--fork-session"),
            parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive, onResponsive = onResponsive        )
    }

    fun createNewWorktreeWidget(
        worktreeName: String,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null,
        onResponsive: (() -> Unit)? = null
    ): TerminalSession {
        return createWidget(
            arrayOf("claude", "--worktree", worktreeName, "--name", worktreeName),
            parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive, onResponsive = onResponsive        )
    }

    fun createNewNamedSessionWidget(
        name: String?,
        parent: Disposable,
        workingDir: String? = null,
        statusFile: Path? = null,
        notifyFile: Path? = null,
        onActiveChanged: ((Boolean) -> Unit)? = null,
        onUserInput: (() -> Unit)? = null,
        onUnresponsive: (() -> Unit)? = null,
        onResponsive: (() -> Unit)? = null
    ): TerminalSession {
        val command = if (name.isNullOrBlank()) arrayOf("claude") else arrayOf("claude", "--name", name)

        return createWidget(command, parent, workingDir = workingDir, statusFile = statusFile, notifyFile = notifyFile,
            onActiveChanged = onActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive, onResponsive = onResponsive)
    }

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
        onResponsive: (() -> Unit)? = null,
        isShell: Boolean = false
    ): TerminalSession {
        val env = com.canopy.util.ProcessHelper.augmentedEnv()
        env["TERM"] = "xterm-256color"
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

        val fullCommand = if (statusFile != null) {
            val statusService = ClaudeStatusService.getInstance(project)
            val wrapperPath = statusService.getWrapperScriptPath()
            val notifyScriptPath = statusService.getNotifyScriptPath()
            val overridePath = statusService.createOverrideSettingsFile(wrapperPath, notifyScriptPath, statusService.getLedgerCommand())

            env["CANOPY_LEDGER_DIR"] = com.canopy.session.SessionLedger.directory().toString()
            env["CANOPY_STATUS_FILE"] = statusFile.toAbsolutePath().toString()
            env["CANOPY_NOTIFY_FILE"] = notifyFile?.toAbsolutePath()?.toString() ?: ""
            env["CANOPY_ORIGINAL_STATUSLINE"] = statusService.discoverOriginalStatusLineCommand() ?: ""

            Disposer.register(parent, Disposable {
                try { Files.deleteIfExists(overridePath) } catch (_: Exception) {}
            })

            arrayOf(resolvedCommand[0], "--settings", overridePath.toAbsolutePath().toString()) +
                resolvedCommand.drop(1).toTypedArray()
        } else {
            resolvedCommand
        }

        // Run through the login shell: as the pty's own process the agent renders inline, with no
        // alternate screen and therefore no mouse, so selection and its clickable parts are dead.
        val throughShell = !isShell && com.canopy.settings.CanopySettings.getInstance().state.runAgentThroughShell
        val shell = env["SHELL"] ?: "/bin/zsh"
        // Typed into an interactive shell rather than passed to one: -c runs without job control,
        // and every arrangement short of the shell actually reading the line has left the agent
        // drawing inline, with no alternate screen and so no mouse.
        val spawned = if (throughShell) arrayOf(shell, "-l") else fullCommand
        val typed = if (throughShell) shellCommandLine(fullCommand.toList()) + "\n" else null

        log.info("Canopy: createWidget — spawned=${spawned.toList()}, PATH=${env["PATH"]?.take(200)}")

        val ptyProcess = try {
            PtyProcessBuilder(spawned)
                .setEnvironment(env)
                .setDirectory(effectiveWorkingDir)
                .setInitialColumns(INITIAL_COLUMNS)
                .setInitialRows(INITIAL_ROWS)
                .start()
        } catch (e: Exception) {
            log.error("Canopy: createWidget — failed to start PTY process: ${fullCommand.toList()}", e)
            throw e
        }

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

        val monitoring = com.canopy.settings.CanopySettings.getInstance().state.detectAgentActivity
        val connector = if (wrappedOnActiveChanged != null && monitoring) {
            val echoTimeout = com.canopy.settings.CanopySettings.getInstance().state.echoTimeoutMs.toLong()
            ActivityMonitoringTtyConnector(ptyProcess, StandardCharsets.UTF_8, echoTimeoutMs = echoTimeout, onActiveChanged = wrappedOnActiveChanged, onUserInput = onUserInput, onUnresponsive = onUnresponsive, onResponsive = onResponsive)
        } else {
            FilteringPtyConnector(ptyProcess, StandardCharsets.UTF_8)
        }

        val settingsProvider = JBTerminalSystemSettingsProvider()
        val widget = JBTerminalWidget(project, settingsProvider, parent)
        widget.addMessageFilter(com.intellij.execution.filters.UrlFilter(project))
        typed?.let { line -> connector.onFirstOutput = { sendInput(ptyProcess, line) } }
        startOnceWide(widget) { widget.start(connector) }
        ClipboardImagePaste.install(widget.terminalPanel) { sendInput(ptyProcess, it) }

        // A file editor is disposed on the EDT, so waiting here for the agent to exit is the UI
        // thread waiting: closing a tab took as long as node took to answer a signal, up to the
        // two seconds below. The signal goes now; whether it worked is somebody else's problem.
        Disposer.register(parent, Disposable {
            if (!ptyProcess.isAlive) return@Disposable

            ptyProcess.destroy()
            com.canopy.util.CanopyExecutor.submit {
                if (!ptyProcess.waitFor(GRACEFUL_EXIT_SECONDS, TimeUnit.SECONDS)) ptyProcess.destroyForcibly()
            }
        })

        return TerminalSession(widget, ptyProcess)
    }

    private fun startOnceWide(widget: JBTerminalWidget, start: () -> Unit) {
        val panel = widget.terminalPanel
        if (panel.width >= MIN_START_WIDTH) return start()

        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        val begin = { if (!started.getAndSet(true)) start() }

        panel.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(event: java.awt.event.ComponentEvent) {
                if (panel.width >= MIN_START_WIDTH) begin()
            }
        })

        javax.swing.Timer(START_FALLBACK_MS) { begin() }.apply { isRepeats = false }.start()
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
        private const val GRACEFUL_EXIT_SECONDS = 2L
        private const val INITIAL_COLUMNS = 120
        private const val INITIAL_ROWS = 40
        private const val MIN_START_WIDTH = 200
        private const val START_FALLBACK_MS = 1500

        fun getInstance(project: Project): ClaudeTerminalService =
            project.getService(ClaudeTerminalService::class.java)
    }
}
