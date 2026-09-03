package com.canopy.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JTextArea

class ReviewNoteDialog(
    project: Project,
    private val files: List<String>,
    private val lines: IntRange?,
    private val roots: Set<String>
) : DialogWrapper(project) {

    private val note = JTextArea(5, 52).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        title = "Send a Note to the Session"
        setOKButtonText("Send")
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = note

    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(JBLabel(subject()).apply { foreground = UIUtil.getLabelDisabledForeground() })
        }
        row {
            scrollCell(note).align(AlignX.FILL)
        }.resizableRow()
    }.apply { border = JBUI.Borders.empty(8) }

    fun prompt(): String? = promptFor(ReviewNote(files, lines, note.text), roots)

    private fun subject(): String {
        val listed = files.map { relativize(it, roots) }

        return when {
            listed.isEmpty() -> "No file selected"
            listed.size == 1 && lines != null -> "${listed.single()}, lines ${lines.first}-${lines.last}"
            listed.size == 1 -> listed.single()
            else -> "${listed.size} files"
        }
    }
}
