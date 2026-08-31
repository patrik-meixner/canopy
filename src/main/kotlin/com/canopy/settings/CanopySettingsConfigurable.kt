package com.canopy.settings

import com.canopy.editor.ClaudeSessionEditor
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class CanopySettingsConfigurable : BoundConfigurable("Canopy") {

    private val state get() = CanopySettings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        group("Session list") {
            row {
                checkBox("Notify me when an agent needs permission").bindSelected(state::notifyWhenBlocked)
            }.rowComment("A balloon when a session stops at a permission prompt. Never for the session already on screen.")

            row {
                checkBox("Notify me when an agent finishes a turn").bindSelected(state::notifyWhenWaiting)
            }.rowComment("Off by default: an agent pauses between every batch of tool calls, so this fires while it is plainly still working.")

            row {
                checkBox("Show blocked agents first").bindSelected(state::sortAttentionFirst)
            }.rowComment("Sessions waiting for permission or for your reply sort above the rest, rather than ordering purely by recency.")

            row("Show sessions from the last") {
                intTextField(1..3650).bindIntText(state::recentSessionDays).columns(4)
                label("days")
            }.rowComment("Older sessions stay one click away behind a toggle, and a search looks through all of them.")
        }

        group("Session tabs") {
            row { checkBox("Toolbar").bindSelected(state::showSessionToolbar).comment("Model, effort, clear, reconnect") }
            row { checkBox("Git row").bindSelected(state::showGitToolbar).comment("Branch, changed files, commit") }
            row { checkBox("Context bar").bindSelected(state::showSessionContextBar).comment("Context, cost, CLI version") }
                .rowComment("Turn these off when you read the same numbers from the Claude Code status line inside the terminal.")
        }

        group("Review") {
            row {
                checkBox("Keep the agent's fullscreen renderer enabled").bindSelected(state::disableFullscreenCanary)
            }.rowComment("Sets CLAUDE_CODE_NO_FLICKER=1. Several sessions at once make the CLI's own boot check count false failures and turn its fullscreen renderer off for the whole machine, which takes mouse selection with it.")

            row {
                checkBox("Start the agent through the login shell").bindSelected(state::runAgentThroughShell)
            }.rowComment("As the terminal's own process the agent renders inline, so its mouse selection and clickable rows do not work. Costs whatever your shell profile costs at startup.")

            row {
                checkBox("Detect whether the agent is working").bindSelected(state::detectAgentActivity)
            }.rowComment("Reads the terminal stream to sort blocked sessions first. Turning it off spawns the agent exactly as a shell tab does, at the cost of the working and waiting states.")

            row {
                checkBox("Show untracked files").bindSelected(state::showUnversionedInDetail)
            }.rowComment("Adds an Unversioned section to the Detail tree. A repository with generated output can carry hundreds of these.")

            row("Re-read git no more often than every") {
                intTextField(0..600).bindIntText(state::detailRevalidateSeconds).columns(4)
                label("seconds")
            }.rowComment("Applies when the Detail tab regains focus. Switching sessions and the Refresh button both ignore it.")
        }

        group("Worktrees") {
            row("Sweep no more often than every") {
                intTextField(1..600).bindIntText(state::worktreeRefreshSeconds).columns(4)
                label("seconds")
            }.rowComment("One git process per repository per sweep, and only while the tab is on screen.")

            row("Branch status refresh") {
                intTextField(0..600).bindIntText(state::branchStatusRefreshSeconds).columns(4)
                label("seconds")
            }.rowComment("0 refreshes on tab focus and status events only. Takes effect on the next session restart.")

            row("Carry these files into a new worktree") {
                textField().bindText(state::worktreeSetupFiles).columns(34)
            }.rowComment("Comma separated, relative to the repository root. The default for repositories with no setup of their own: right-click a repository or submodule in Worktrees to give it its own list.")

            row("Then run") {
                textField().bindText(state::worktreeSetupCommand).columns(34)
            }.rowComment("For example pnpm install. Runs in a terminal tab so the output is readable. Leave empty to skip.")
        }

        group("Claude Code") {
            row("Binary path") {
                textField().bindText(state::claudeBinaryPath).columns(34)
            }.rowComment("Leave empty to find claude on PATH.")

            row("Default arguments") {
                textField().bindText(state::defaultSessionArgs).columns(34)
            }.rowComment("Appended to every session launch, for example --model opus.")

            row("Unresponsive after") {
                intTextField(1000..60000).bindIntText(state::echoTimeoutMs).columns(6)
                label("ms without a response")
            }

            row("Status line refresh") {
                intTextField(0..3600).bindIntText(state::statusLineRefreshInterval).columns(4)
                label("seconds")
            }.rowComment("0 updates after each assistant message only. Requires CLI 2.1.97 or newer.")
        }

        group("Environment") {
            row {
                link("Claude Code environment variable documentation") {
                    BrowserUtil.browse("https://docs.anthropic.com/en/docs/claude-code/settings#environment-variables")
                }
            }
            row { checkBox("True colour output").bindSelected(state::envColorterm).comment("COLORTERM=truecolor") }
            row { checkBox("Disable telemetry").bindSelected(state::envDisableNonessentialTraffic).comment("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1") }
            row { checkBox("Skip update check").bindSelected(state::envSkipUpdateCheck).comment("CLAUDE_CODE_SKIP_UPDATE_CHECK=1") }
            row { checkBox("Disable prompt caching").bindSelected(state::envDisablePromptCaching).comment("DISABLE_PROMPT_CACHING=1") }

            row("Additional") {
                textArea().bindText(state::customEnvVars).rows(3).columns(34)
            }.layout(RowLayout.LABEL_ALIGNED)
                .rowComment("One KEY=VALUE per line.")
        }

        group("Support") {
            row {
                link("Buy me a coffee") { BrowserUtil.browse("https://ko-fi.com/patrikmeixner") }
            }.rowComment("Canopy is free, and stays free. Nothing is locked behind a payment.")
        }

        group("Advanced") {
            row("Background threads") {
                intTextField(2..64).bindIntText(state::backgroundPoolMaxThreads).columns(4)
            }.rowComment("Cap on Canopy's own pool for git polling and status checks. Raise it if the IDE log warns the pool is saturated.")
        }
    }

    /** The bound panel writes the values; open windows still have to be told to redraw. */
    override fun apply() {
        super.apply()
        refreshOpenSessionChrome()
    }

    private fun refreshOpenSessionChrome() {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue

            FileEditorManager.getInstance(project).allEditors
                .filterIsInstance<ClaudeSessionEditor>()
                .forEach { it.refreshChromeVisibility() }
        }
    }
}
