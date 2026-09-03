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
        @JvmField var echoTimeoutMs: Int = 3000

        @JvmField var detectAgentActivity: Boolean = true

        @JvmField var runAgentThroughShell: Boolean = false

        @JvmField var disableFullscreenCanary: Boolean = true

        @JvmField var claudeBinaryPath: String = ""

        @JvmField var defaultSessionArgs: String = ""

        @JvmField var envColorterm: Boolean = false
        @JvmField var envDisableNonessentialTraffic: Boolean = false
        @JvmField var envSkipUpdateCheck: Boolean = false
        @JvmField var envDisablePromptCaching: Boolean = false

        @JvmField var customEnvVars: String = ""

        @JvmField var worktreeSetupFiles: String = ".env"

        @JvmField var worktreeSetupCommand: String = ""

        @JvmField var worktreeSetupFilesByRepo: MutableMap<String, String> = LinkedHashMap()

        @JvmField var worktreeSetupCommandByRepo: MutableMap<String, String> = LinkedHashMap()

        @JvmField var notifyWhenBlocked: Boolean = true

        @JvmField var notifyWhenWaiting: Boolean = false

        @JvmField var restoreSessions: SessionRestore = SessionRestore.All

        @JvmField var recentSessionDays: Int = 14

        @JvmField var sortAttentionFirst: Boolean = false

        @JvmField var excludeWorktreesFromIndex: Boolean = true

        @JvmField var showUnversionedInDetail: Boolean = true

        @JvmField var detailRevalidateSeconds: Int = 2

        @JvmField var worktreeRefreshSeconds: Int = 15

        @JvmField var showSessionToolbar: Boolean = false

        @JvmField var showGitToolbar: Boolean = false

        @JvmField var showSessionContextBar: Boolean = true

        @JvmField var statusLineRefreshInterval: Int = 60

        @JvmField var branchStatusRefreshSeconds: Int = 10

        @JvmField var backgroundPoolMaxThreads: Int = 16
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun resolveClaudeBinary(): String {
        val override = myState.claudeBinaryPath.trim()
        if (override.isNotEmpty()) return override
        return com.canopy.util.ProcessHelper.which("claude") ?: "claude"
    }

    fun extraSessionArgs(): List<String> {
        val raw = myState.defaultSessionArgs.trim()
        if (raw.isEmpty()) return emptyList()
        return raw.split("\\s+".toRegex())
    }

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
