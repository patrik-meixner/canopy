package com.canopy.services

import com.canopy.model.RepoScope
import com.canopy.util.CanopyExecutor
import com.canopy.util.RepoScopeDiscovery
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Caches the project's repo scopes, since discovery costs one git process per submodule plus
 * one for the superproject and the answer only changes when a submodule is added or checked out.
 *
 * Reads never block: callers get the last known list, and a stale one triggers a background
 * refresh that calls back when it lands.
 */
@Service(Service.Level.PROJECT)
class RepoScopeService(private val project: Project) {

    private val refreshing = AtomicBoolean(false)

    @Volatile private var scopes: List<RepoScope> = emptyList()
    @Volatile private var computedAt = 0L

    fun cachedScopes(): List<RepoScope> = scopes

    fun refreshIfStale(onUpdated: () -> Unit) {
        if (System.currentTimeMillis() - computedAt < STALE_AFTER_MS) return

        refresh(onUpdated)
    }

    fun refresh(onUpdated: () -> Unit) {
        val basePath = project.basePath ?: return
        if (!refreshing.compareAndSet(false, true)) return

        CanopyExecutor.submit {
            try {
                val discovered = RepoScopeDiscovery.discover(basePath)
                val hasChanged = discovered != scopes
                scopes = discovered
                computedAt = System.currentTimeMillis()
                if (hasChanged) onUpdated()
            } finally {
                refreshing.set(false)
            }
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 600_000L

        fun getInstance(project: Project): RepoScopeService =
            project.getService(RepoScopeService::class.java)
    }
}
