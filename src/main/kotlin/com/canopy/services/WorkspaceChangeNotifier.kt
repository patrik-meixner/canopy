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

/**
 * Tells the review panels that the working trees moved, so nothing has to be refreshed by hand.
 *
 * One subscription for the whole project rather than one per panel, and a debounce because a single
 * commit rewrites several files inside .git and an agent writes in bursts.
 */
@Service(Service.Level.PROJECT)
class WorkspaceChangeNotifier(project: Project) : Disposable {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) = schedule()
            }
        )
    }

    fun addListener(parent: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parent, Disposable { listeners.remove(listener) })
    }

    private fun schedule() {
        if (listeners.isEmpty()) return

        alarm.cancelAllRequests()
        alarm.addRequest({ listeners.forEach { it() } }, DEBOUNCE_MS)
    }

    override fun dispose() = listeners.clear()

    companion object {
        private const val DEBOUNCE_MS = 700

        fun getInstance(project: Project): WorkspaceChangeNotifier =
            project.getService(WorkspaceChangeNotifier::class.java)
    }
}
