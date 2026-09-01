package com.canopy.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "CanopySettings",
    storages = [Storage("canopy.xml")]
)
@Service(Service.Level.APP)
class CanopySettings : PersistentStateComponent<CanopySettings.State> {

    class State {
        /** Milliseconds before a terminal session is considered unresponsive after user input. */
        @JvmField var echoTimeoutMs: Int = 3000

        /** Wraps the terminal stream to tell working from waiting. Off spawns the agent exactly as a shell tab does. */
        @JvmField var detectAgentActivity: Boolean = true

        /** Starts the agent as a child of the login shell, which is the only arrangement where its mouse works. */
        @JvmField var runAgentThroughShell: Boolean = false

        /** Keeps the CLI's fullscreen boot canary from striking, which several sessions at once falsely trip. */
        @JvmField var disableFullscreenCanary: Boolean = true

        /** Manual override for the claude binary path. Empty string means auto-detect. */
        @JvmField var claudeBinaryPath: String = ""

        /** Extra CLI arguments appended when launching new sessions (space-separated). */
        @JvmField var defaultSessionArgs: String = ""

        // --- Environment variable toggles ---
        @JvmField var envColorterm: Boolean = false
        @JvmField var envDisableNonessentialTraffic: Boolean = false
        @JvmField var envSkipUpdateCheck: Boolean = false
        @JvmField var envDisablePromptCaching: Boolean = false

        /** Additional custom env vars (KEY=VALUE per line). */
        @JvmField var customEnvVars: String = ""

        /** Untracked files carried into a fresh worktree, comma or newline separated. */
        @JvmField var worktreeSetupFiles: String = ".env"

        /** Shell command run in a fresh worktree once its files are in place. */
        @JvmField var worktreeSetupCommand: String = ""

        /** Per-repository file lists, keyed by repository label, overriding [worktreeSetupFiles]. */
        @JvmField var worktreeSetupFilesByRepo: MutableMap<String, String> = LinkedHashMap()

        /** Per-repository setup commands, keyed by repository label, overriding [worktreeSetupCommand]. */
        @JvmField var worktreeSetupCommandByRepo: MutableMap<String, String> = LinkedHashMap()

        /** Raise a notification when an agent stops and waits for permission or for a reply. */
        @JvmField var notifyWhenBlocked: Boolean = true

        /**
         * Notify when a session merely finishes a turn. Off by default: an agent pauses between
         * every batch of tool calls, so this fires while it is plainly still working.
         */
        @JvmField var notifyWhenWaiting: Boolean = false

        /** Days of history the session list shows before folding the rest behind a toggle. */
        @JvmField var recentSessionDays: Int = 14

        /** Blocked agents sort above the rest, instead of ordering purely by recency. */
        @JvmField var sortAttentionFirst: Boolean = false

        /** Keep every .claude/worktrees directory out of the IDE's index. */
        @JvmField var excludeWorktreesFromIndex: Boolean = true

        /** Include untracked files as their own section in the Detail tree. */
        @JvmField var showUnversionedInDetail: Boolean = true

        /** Floor between the Detail tab re-reading git when it regains focus. */
        @JvmField var detailRevalidateSeconds: Int = 2

        /** Floor between Worktrees tab sweeps. Each costs one git process per repository. */
        @JvmField var worktreeRefreshSeconds: Int = 15


        /** Show the session tab's own toolbar: model switcher, effort, clear, reconnect, Messages. */
        @JvmField var showSessionToolbar: Boolean = false

        /** Show the git row of each session tab: branch, changed-file count, Commit. */
        @JvmField var showGitToolbar: Boolean = false

        /** Show the context/effort/cost bar along the bottom of each session tab. */
        @JvmField var showSessionContextBar: Boolean = true

        /** Status line refresh interval in seconds. 0 means event-driven only. */
        @JvmField var statusLineRefreshInterval: Int = 60

        /** Seconds between auto-refreshes of the git/worktree toolbar branch status. 0 = focus + status events only. */
        @JvmField var branchStatusRefreshSeconds: Int = 10

        /**
         * Max concurrent background threads in the Canopy-owned pool. Sized to cover
         * peak per-tab refresh work (each open session tab can have up to 2 in-flight
         * refreshes — git toolbar + worktree status — gated by single-flight). Default
         * 16 covers ~8 session tabs across multiple IDE windows. If the pool runs hot
         * a warning is logged so this can be tuned upward.
         */
        @JvmField var backgroundPoolMaxThreads: Int = 16
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** Returns the configured binary path, or auto-detects via PATH if empty. */
    fun resolveClaudeBinary(): String {
        val override = myState.claudeBinaryPath.trim()
        if (override.isNotEmpty()) return override
        return com.canopy.util.ProcessHelper.which("claude") ?: "claude"
    }

    /** Returns extra session args split into a list, or empty if none configured. */
    fun extraSessionArgs(): List<String> {
        val raw = myState.defaultSessionArgs.trim()
        if (raw.isEmpty()) return emptyList()
        return raw.split("\\s+".toRegex())
    }

    /** Returns env vars to set based on toggle and custom settings. */
    fun environmentOverrides(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        if (myState.envColorterm) env["COLORTERM"] = "truecolor"
        if (myState.disableFullscreenCanary) env["CLAUDE_CODE_NO_FLICKER"] = "1"
        if (myState.envDisableNonessentialTraffic) env["CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"] = "1"
        if (myState.envSkipUpdateCheck) env["CLAUDE_CODE_SKIP_UPDATE_CHECK"] = "1"
        if (myState.envDisablePromptCaching) env["DISABLE_PROMPT_CACHING"] = "1"
        for (line in myState.customEnvVars.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.contains('=')) continue
            val key = trimmed.substringBefore('=')
            val value = trimmed.substringAfter('=')
            env[key] = value
        }
        return env
    }

    companion object {
        fun getInstance(): CanopySettings =
            ApplicationManager.getApplication().getService(CanopySettings::class.java)
    }
}
