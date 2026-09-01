package com.canopy.toolwindow

import com.canopy.insight.InsightUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * The Commit window's message box, where the review already is.
 *
 * A modal input dialog for a commit message hides the tree behind it, which is the one thing worth
 * looking at while writing one. This is the platform's own editor, so it brings the commit-message
 * highlighting, the history and the inspections with it - and it is only on screen while a commit
 * is actually being written.
 */
class SessionCommitPanel(
    project: Project,
    parent: Disposable,
    private val onCommit: (message: String, after: AfterCommit) -> Unit,
    private val onCancel: () -> Unit
) : JPanel(BorderLayout()) {

    private val message = CommitMessage(project, false, true, true)
    private val caption = JBLabel().apply {
        border = JBUI.Borders.empty(0, 4)
        foreground = UIUtil.getLabelDisabledForeground()
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }
    private val commit = JButton("Commit")
    private val commitAndPush = JButton("Commit and Push")

    init {
        Disposer.register(parent, message)
        isVisible = false
        border = JBUI.Borders.empty(InsightUi.GAP / 2, InsightUi.GAP, InsightUi.GAP, InsightUi.GAP)
        message.preferredSize = Dimension(0, JBUI.scale(EDITOR_HEIGHT))

        commit.addActionListener { accept(AfterCommit.Stay) }
        commitAndPush.addActionListener { accept(AfterCommit.Push) }
        val cancel = JButton("Cancel").apply { addActionListener { hidePanel() } }

        add(caption, BorderLayout.NORTH)
        add(message, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(cancel)
            add(commit)
            add(commitAndPush)
        }, BorderLayout.SOUTH)

        registerKeyboardAction({ hidePanel() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    }

    /** The message survives being cancelled: a commit abandoned to look at a diff is not rewritten. */
    fun ask(text: String) {
        caption.text = text
        isVisible = true
        revalidate()
        message.requestFocusInMessage()
    }

    fun hidePanel() {
        if (!isVisible) return

        isVisible = false
        revalidate()
        onCancel()
    }

    /** An empty message is not a commit, and git would refuse it anyway. */
    private fun accept(after: AfterCommit) {
        val typed = message.text.trim()
        if (typed.isEmpty()) return message.requestFocusInMessage()

        message.setText("")
        isVisible = false
        revalidate()
        onCommit(typed, after)
    }

    override fun getPreferredSize(): Dimension =
        if (isVisible) super.getPreferredSize() else Dimension(0, 0)

    private companion object {
        const val EDITOR_HEIGHT = 90
    }
}
