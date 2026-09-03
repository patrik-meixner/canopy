package com.canopy.services

import com.canopy.editor.ClaudeSessionEditor
import com.canopy.editor.ClaudeSessionVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class FanOutPrompts(private val project: Project) {

    private val pending = ConcurrentHashMap<String, String>()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, ClaudeSessionService.getInstance(project))

    fun expect(worktreeNames: List<String>, prompt: String) {
        worktreeNames.forEach { pending[it] = prompt }
        schedule(0)
    }

    private fun schedule(attempt: Int) {
        if (pending.isEmpty() || attempt >= MAX_ATTEMPTS) return

        alarm.addRequest({
            deliver()
            schedule(attempt + 1)
        }, RETRY_MS)
    }

    private fun deliver() {
        val manager = FileEditorManager.getInstance(project)
        for (file in manager.openFiles.filterIsInstance<ClaudeSessionVirtualFile>()) {
            val name = file.newWorktreeName ?: file.baseName
            val prompt = pending[name] ?: continue
            val editor = manager.getEditors(file).filterIsInstance<ClaudeSessionEditor>().firstOrNull() ?: continue
            if (editor.terminal() == null) continue

            pending.remove(name)
            ApplicationManager.getApplication().invokeLater { editor.sendToTerminal("$prompt\r") }
        }
    }

    companion object {
        private const val RETRY_MS = 1500
        private const val MAX_ATTEMPTS = 40

        fun getInstance(project: Project): FanOutPrompts = project.getService(FanOutPrompts::class.java)
    }
}
