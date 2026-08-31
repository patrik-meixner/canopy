package com.canopy.toolwindow

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path

data class FileProblem(
    val path: String,
    val line: Int,
    val message: String
)

/**
 * Errors and warnings the IDE found in the files a session changed.
 *
 * The agent cannot see the IDE's analysis, so it ships code the editor is already underlining. The
 * highlights come from files whose documents are loaded, which is what the daemon has analysed.
 */
object TouchedFileProblems {

    private const val PER_FILE_LIMIT = 10

    fun collect(project: Project, paths: Collection<String>): List<FileProblem> =
        paths.flatMap { path -> problemsIn(project, path) }

    private fun problemsIn(project: Project, path: String): List<FileProblem> {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(path)) ?: return emptyList()
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return emptyList()

        return runCatching { highlightsIn(project, document, path) }.getOrDefault(emptyList())
    }

    private fun highlightsIn(project: Project, document: Document, path: String): List<FileProblem> =
        DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project)
            .filter { !it.description.isNullOrBlank() }
            .take(PER_FILE_LIMIT)
            .map { FileProblem(path, document.getLineNumber(it.startOffset) + 1, it.description) }
}

private const val MAX_LISTED = 25

/** One prompt the agent can act on, rather than a dump it has to re-derive locations from. */
fun problemPrompt(problems: List<FileProblem>, repositoryRoots: Set<String>): String? {
    if (problems.isEmpty()) return null
    val listed = problems.take(MAX_LISTED)
    val rest = problems.size - listed.size
    val lines = listed.joinToString("\n") { "- ${relativize(it.path, repositoryRoots)}:${it.line} ${it.message}" }
    val tail = if (rest > 0) "\n- and $rest more" else ""

    return "The IDE reports these problems in what you changed. Fix them:\n$lines$tail\r"
}
