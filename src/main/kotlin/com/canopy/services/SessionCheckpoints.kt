package com.canopy.services

import com.canopy.settings.CanopySettings
import com.canopy.toolwindow.Checkpoint
import com.canopy.toolwindow.Checkpoints
import com.canopy.toolwindow.checkpointLabel
import com.canopy.util.CanopyExecutor
import com.canopy.util.ProcessHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Snapshots a session's working tree when its turn ends.
 *
 * A turn boundary is the only moment where "before the agent did that" is a coherent state, and it
 * is the moment you want back when the next turn goes wrong.
 */
@Service(Service.Level.PROJECT)
class SessionCheckpoints(private val project: Project) {

    private val roots = ConcurrentHashMap<String, String>()

    fun onTurnEnded(sessionId: String, notifyState: String) {
        if (notifyState != TURN_END) return
        if (!CanopySettings.getInstance().state.checkpointOnTurnEnd) return

        CanopyExecutor.submit {
            val root = rootFor(sessionId) ?: return@submit

            Checkpoints.create(root, sessionId, checkpointLabel(System.currentTimeMillis()))
        }
    }

    fun list(sessionId: String): List<Checkpoint> {
        val root = rootFor(sessionId) ?: return emptyList()

        return Checkpoints.list(root, sessionId)
    }

    fun restore(sessionId: String, checkpoint: Checkpoint): Pair<Boolean, String> {
        val root = rootFor(sessionId) ?: return false to "No repository for this session"

        return Checkpoints.restore(root, checkpoint.commit)
    }

    fun rootFor(sessionId: String): String? {
        val workingDir = ClaudeSessionService.getInstance(project).cachedWorkingDir(sessionId)
            ?: project.basePath
            ?: return null

        return roots.getOrPut(workingDir) { topLevelOf(workingDir) ?: "" }.ifEmpty { null }
    }

    /** A worktree is its own top level, so a session running in one checkpoints only its own tree. */
    private fun topLevelOf(directory: String): String? = try {
        val result = ProcessHelper.execWithTimeout(
            arrayOf("git", "-C", directory, "rev-parse", "--show-toplevel"),
            10_000,
            extraEnv = mapOf("GIT_OPTIONAL_LOCKS" to "0")
        )
        if (result.exitCode == 0) result.output.trim().ifEmpty { null } else null
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TURN_END = "idle_prompt"

        fun getInstance(project: Project): SessionCheckpoints =
            project.getService(SessionCheckpoints::class.java)
    }
}
