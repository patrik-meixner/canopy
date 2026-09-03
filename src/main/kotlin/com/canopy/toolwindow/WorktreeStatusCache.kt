package com.canopy.toolwindow

import com.canopy.util.ClaudePathEncoder
import com.canopy.util.CanopyExecutor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import java.awt.event.HierarchyEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

class WorktreeStatusCache(
    private val project: Project,
    parent: Disposable,
    private val onUpdated: () -> Unit
) : Disposable {

    private companion object {
        const val MIN_SWEEP_INTERVAL_MS = 15_000L
        const val PERIODIC_MS = 30_000L
    }

    private val cache = ConcurrentHashMap<String, WorktreeStatus>()
    private val inFlight = AtomicBoolean(false)
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    @Volatile private var targets: List<String> = emptyList()
    @Volatile private var visible = false
    @Volatile private var lastSweepAt = 0L

    init {
        Disposer.register(parent, this)
        alarm.addRequest(::tick, PERIODIC_MS)
    }

    fun get(worktreeName: String): WorktreeStatus? = cache[worktreeName]

    fun setTargets(names: Collection<String>) {
        targets = names.distinct()
    }

    fun trackVisibility(component: JComponent) {
        visible = component.isShowing
        component.addHierarchyListener { e ->
            if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener
            val nowVisible = component.isShowing
            val became = nowVisible && !visible
            visible = nowVisible
            if (became) requestRefresh(force = true)
        }
    }

    fun requestRefresh(force: Boolean = false) {
        if (!visible) return
        val names = targets
        if (names.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSweepAt < MIN_SWEEP_INTERVAL_MS) return
        if (!inFlight.compareAndSet(false, true)) return
        lastSweepAt = now

        CanopyExecutor.submit {
            try {
                sweep(names)
            } finally {
                inFlight.set(false)
            }
        }
    }

    /** Sequential on purpose: N worktrees must not mean N git processes at once. */
    private fun sweep(names: List<String>) {
        val basePath = project.basePath ?: return
        val mainBranch = WorktreeInspector.currentBranch(basePath)
        var changed = false
        for (name in names) {
            val path = ClaudePathEncoder.worktreeAbsolutePath(basePath, name)
            val status = WorktreeInspector.status(path, basePath, mainBranch)
            if (cache.put(name, status) != status) changed = true
        }
        if (changed) ApplicationManager.getApplication().invokeLater(onUpdated)
    }

    private fun tick() {
        requestRefresh()
        if (!alarm.isDisposed) alarm.addRequest(::tick, PERIODIC_MS)
    }

    override fun dispose() {
        cache.clear()
    }
}
