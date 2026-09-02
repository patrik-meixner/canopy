package com.canopy.services

import com.canopy.settings.CanopySettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
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

    /** Instant, and empty until the first walk has landed: nothing is excluded that was not found. */
    fun current(): Array<String> {
        if (!CanopySettings.getInstance().state.excludeWorktreesFromIndex) return emptyArray()
        if (started.compareAndSet(false, true)) alarm.addRequest(::recompute, 0)

        return urls
    }

    private fun recompute() {
        val base = project.basePath
        val found = if (base == null) emptyArray() else worktreeExcludeUrls(base).toTypedArray()

        if (!found.contentEquals(urls)) {
            urls = found
            requestRootsRescan(project)
        }
        if (!alarm.isDisposed) alarm.addRequest(::recompute, REVISIT_MS)
    }

    override fun dispose() = Unit

    companion object {
        /** Worktrees appear when an agent starts one; a minute late is invisible, a walk a second is not. */
        private const val REVISIT_MS = 60_000

        fun getInstance(project: Project): WorktreeExclusions = project.getService(WorktreeExclusions::class.java)
    }
}
