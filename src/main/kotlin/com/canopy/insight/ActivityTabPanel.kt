package com.canopy.insight

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.BorderLayout
import java.nio.file.Path

/**
 * What the session did, turn by turn.
 *
 * A flat log of tool calls answers "what calls were made", which nobody asks: it buries the one
 * edit under twelve greps and reads as the terminal again, only worse. A turn is the unit the work
 * was asked for in, so it is the unit it reads back in.
 */
class ActivityTabPanel(project: Project, parent: Disposable) : InsightTabPanel(project, parent) {

    private val cards = CardListPanel(
        EmptyState(
            AllIcons.Actions.ListFiles,
            "Nothing recorded yet",
            "Each of your prompts becomes a turn here, with the files it wrote and what it ran to get there."
        )
    )
    private var turns: List<ActivityTurn> = emptyList()
    private var writesOnly = false
    private var limit = TURN_PAGE

    init {
        background = InsightUi.panelBackground()
        add(toolbar(), BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
    }

    override fun render(insight: SessionInsight) {
        val prompts = insight.messages.associate { it.ordinal to it.headline }

        turns = activityTurns(insight.activity, prompts, writesOnly).asReversed()
        rebuild()
    }

    private fun rebuild() {
        val page = pageful(turns, limit)

        cards.onMore(page.remaining, TURN_PAGE) {
            limit += TURN_PAGE
            rebuild()
        }
        cards.setCards(page.shown.map { TurnCard(it, ::openFile) }, page.shown.map(::turnCardKey))
    }

    private fun toolbar(): javax.swing.JComponent {
        val group = DefaultActionGroup()
        group.add(object : ToggleAction("Turns That Wrote Something", "Hide turns that only read and searched", AllIcons.Actions.Edit) {
            override fun isSelected(event: AnActionEvent) = writesOnly

            override fun setSelected(event: AnActionEvent, state: Boolean) {
                writesOnly = state
                limit = TURN_PAGE
                refresh()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })

        return ActionManager.getInstance().createActionToolbar("CanopyActivity", group, true)
            .also { it.targetComponent = this }
            .component
    }

    private fun openFile(path: String) {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(path)) ?: return

        FileEditorManager.getInstance(project).openFile(file, true)
    }
}

private const val TURN_PAGE = 30
