package com.canopy.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy

/** Answers from a cache: this is called under a lock the UI thread waits on. */
class CanopyWorktreeExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> = WorktreeExclusions.getInstance(project).current()
}
