package com.canopy.toolwindow

import com.canopy.util.ProcessHelper

data class BranchCommits(val base: String?, val revisions: List<String>) {

    companion object {

        private const val GIT_TIMEOUT_MS = 15_000L
        private const val REMEMBERED = 64

        private val resolved = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<Pair<String, String>, BranchCommits>(REMEMBERED, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<Pair<String, String>, BranchCommits>) = size > REMEMBERED
            }
        )

        fun of(root: String): BranchCommits? {
            val head = git(root, "rev-parse", "--verify", "--quiet", "HEAD") ?: return null

            return resolved.getOrPut(root to head) { resolve(root) }
        }

        private fun resolve(root: String): BranchCommits {
            val defaultBranch = defaultBranchOf(git(root, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")) { branch ->
                git(root, "rev-parse", "--verify", "--quiet", branch) != null
            }
            val base = defaultBranch?.let { git(root, "merge-base", it, "HEAD") }
            if (base != null) return BranchCommits(base, listOf("$base..HEAD"))

            val upstream = git(root, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}")

            return BranchCommits(null, listOfNotNull("HEAD", "--not", upstream?.let { "--exclude=$it" }, "--remotes"))
        }

        private fun git(root: String, vararg args: String): String? = try {
            val result = ProcessHelper.execWithTimeout(
                com.canopy.util.gitCommand(root, *args),
                GIT_TIMEOUT_MS,
                extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
            )
            result.output.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
