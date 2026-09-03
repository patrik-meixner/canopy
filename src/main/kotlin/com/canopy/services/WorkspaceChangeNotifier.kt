package com.canopy.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class WorkspaceChangeNotifier(private val project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val gitAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private var gitStamps: Map<String, Long> = emptyMap()

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { changesWorkingTree(it.path) }) schedule()
                }
            }
        )
    }

    fun addListener(parent: Disposable, listener: () -> Unit) {
        val wasEmpty = listeners.isEmpty()
        listeners.add(listener)
        Disposer.register(parent, Disposable { listeners.remove(listener) })

        if (wasEmpty) pollGitState()
    }

    private fun pollGitState() {
        if (gitAlarm.isDisposed) return

        gitAlarm.addRequest({
            if (listeners.isNotEmpty()) {
                val stamps = RepoScopeService.getInstance(project).cachedScopes()
                    .associate { it.root to gitStateStamp(it.root) }
                if (gitStamps.isNotEmpty() && stamps != gitStamps) schedule()
                gitStamps = stamps
            }
            pollGitState()
        }, GIT_POLL_MS)
    }

    private fun schedule() {
        if (listeners.isEmpty()) return

        alarm.cancelAllRequests()
        alarm.addRequest({ listeners.forEach { it() } }, DEBOUNCE_MS)
    }

    override fun dispose() = listeners.clear()

    companion object {
        private const val DEBOUNCE_MS = 700

        private const val GIT_POLL_MS = 2_000

        fun getInstance(project: Project): WorkspaceChangeNotifier =
            project.getService(WorkspaceChangeNotifier::class.java)
    }
}
