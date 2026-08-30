package com.canopy.toolwindow

import com.canopy.settings.CanopySettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Per-repository worktree setup, because what a fresh worktree is missing differs by repository:
 * a `.env` in one, a `devConfig-local.ts` in a submodule, a different install command in each.
 */
class WorktreeSetupDialog(
    private val project: Project,
    private val repoKey: String,
    private val repoRoot: String
) : DialogWrapper(project) {

    private val state = CanopySettings.getInstance().state
    private val filesField = JBTextArea(setupFilesFor(state.worktreeSetupFilesByRepo, repoKey, state.worktreeSetupFiles), 6, 40)
    private val commandField = JBTextField(setupCommandFor(state.worktreeSetupCommandByRepo, repoKey, state.worktreeSetupCommand))

    init {
        title = "Worktree Setup for $repoKey"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Files to copy") {
            scrollCell(filesField).align(AlignX.FILL)
        }.layout(RowLayout.LABEL_ALIGNED)
            .rowComment("One per line, relative to the repository root. Copied from ${repoRoot}, never overwriting a file the worktree already has.")

        row {
            button("Detect Ignored Config Files") { fillDetected() }
        }.rowComment("Lists the files git ignores near the repository root, which is where local configuration usually sits.")

        row("Then run") {
            cell(commandField).align(AlignX.FILL)
        }.rowComment("For example pnpm install. Runs in a terminal tab. Leave empty to skip.")
    }

    private fun fillDetected() {
        val detected = IgnoredFileCandidates.detect(repoRoot)
        if (detected.isEmpty()) return

        val merged = (setupFileNames(filesField.text) + detected).distinct()
        filesField.text = merged.joinToString("\n")
    }

    override fun doOKAction() {
        state.worktreeSetupFilesByRepo[repoKey] = setupFileNames(filesField.text).joinToString(",")
        state.worktreeSetupCommandByRepo[repoKey] = commandField.text.trim()

        super.doOKAction()
    }
}

/** An empty entry is a deliberate "nothing here", so only an absent one falls back to the default. */
internal fun setupFilesFor(overrides: Map<String, String>, repoKey: String, fallback: String): String =
    (overrides[repoKey] ?: fallback).replace(',', '\n')

internal fun setupCommandFor(overrides: Map<String, String>, repoKey: String, fallback: String): String =
    overrides[repoKey] ?: fallback
