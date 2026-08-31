package com.canopy.toolwindow

import com.canopy.model.RepoScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JTextArea

/**
 * One prompt, several worktrees, so three approaches to the same problem can run at once.
 */
class FanOutDialog(
    project: Project,
    private val scope: RepoScope,
    private val existingNames: Set<String>
) : DialogWrapper(project) {

    private val prompt = JTextArea(6, 52).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private val nameField = com.intellij.ui.components.JBTextField()
    private val countField = com.intellij.ui.components.JBTextField("3")

    init {
        title = "Run in Several Worktrees"
        setOKButtonText("Start")
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = prompt

    override fun createCenterPanel(): JComponent = panel {
        row("Repository") { label(scope.label) }

        row("Name them") {
            cell(nameField).columns(24)
            label("worktrees:")
            cell(countField).columns(3)
        }

        row {
            scrollCell(prompt).align(AlignX.FILL)
        }.resizableRow()
            .rowComment("Sent to every session once it starts.")
    }.apply { border = JBUI.Borders.empty(8) }

    fun plan(): FanOutPlan? {
        val text = prompt.text.trim()
        if (text.isEmpty()) return null
        val stem = nameField.text.ifBlank { text }
        val count = countField.text.trim().toIntOrNull()?.coerceIn(1, 8) ?: return null

        return FanOutPlan(scope, fanOutNames(stem, count, existingNames), text)
    }
}

data class FanOutPlan(val scope: RepoScope, val names: List<String>, val prompt: String)
