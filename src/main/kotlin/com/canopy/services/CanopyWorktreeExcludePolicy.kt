package com.canopy.services

import com.canopy.settings.CanopySettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy

/**
 * Keeps the agents' worktrees out of the index without touching the module model, so the setting
 * can be turned off again and nothing of the user's has been rewritten.
 */
class CanopyWorktreeExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> {
        if (!CanopySettings.getInstance().state.excludeWorktreesFromIndex) return emptyArray()

        val roots = listOfNotNull(project.basePath) +
            com.intellij.openapi.roots.ProjectRootManager.getInstance(project)
                .contentRoots
                .mapNotNull { it.canonicalPath }

        return worktreeExcludeUrls(roots.distinct()).toTypedArray()
    }
}
