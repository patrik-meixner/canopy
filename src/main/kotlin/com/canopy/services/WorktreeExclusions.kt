package com.canopy.services

import com.canopy.settings.CanopySettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The policy is consulted while the index is being built, under a lock the UI thread waits on, so
 * whatever it does there the whole IDE does too. The walk happens here, in the background; the
 * policy hands over the last answer, and when that changes the index is asked to look again.
 */
@Service(Service.Level.PROJECT)
class WorktreeExclusions(private val project: Project) : Disposable {

    @Volatile
    private var urls: Array<String> = emptyArray()
    private val started = AtomicBoolean(false)
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    fun current(): Array<String> {
        if (!CanopySettings.getInstance().state.excludeWorktreesFromIndex) return emptyArray()
        if (started.compareAndSet(false, true)) start()

        return urls
    }

    private fun start() {
        alarm.addRequest(::recompute, 0)
        val base = project.basePath ?: return
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { movesWorktrees(it, base) }) recomputeSoon()
                }
            }
        )
    }

    private fun movesWorktrees(event: VFileEvent, base: String): Boolean = when (event) {
        is VFileCreateEvent -> mayMoveWorktrees(event.path, event.isDirectory, base)
        is VFileDeleteEvent -> mayMoveWorktrees(event.path, event.file.isDirectory, base)
        else -> false
    }

    private fun recomputeSoon() {
        alarm.cancelAllRequests()
        alarm.addRequest(::recompute, SETTLE_MS)
    }

    private fun recompute() {
        val base = project.basePath
        val found = if (base == null) emptyArray() else worktreeExcludeUrls(base).toTypedArray()

        if (!found.contentEquals(urls)) {
            urls = found
            requestRootsRescan(project)
        }
    }

    override fun dispose() = Unit

    companion object {
        private const val SETTLE_MS = 1_000

        fun getInstance(project: Project): WorktreeExclusions = project.getService(WorktreeExclusions::class.java)
    }
}
