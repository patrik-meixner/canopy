package com.canopy.toolwindow

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
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

object TouchedFileProblems {

    private const val PER_FILE_LIMIT = 10

    fun collect(project: Project, paths: Collection<String>): List<FileProblem> =
        paths.flatMap { path -> problemsIn(project, path) }

    private fun problemsIn(project: Project, path: String): List<FileProblem> {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(path)) ?: return emptyList()
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return emptyList()

        return runCatching { highlightsIn(project, document, path) }.getOrDefault(emptyList())
    }

    private fun highlightsIn(project: Project, document: Document, path: String): List<FileProblem> {
        val found = mutableListOf<FileProblem>()

        DaemonCodeAnalyzerEx.processHighlights(
            document,
            project,
            HighlightSeverity.WARNING,
            0,
            document.textLength
        ) { info ->
            val description = info.description
            if (!description.isNullOrBlank()) {
                found.add(FileProblem(path, document.getLineNumber(info.startOffset) + 1, description))
            }
            found.size < PER_FILE_LIMIT
        }

        return found
    }
}

private const val MAX_LISTED = 25

fun problemPrompt(problems: List<FileProblem>, repositoryRoots: Set<String>): String? {
    if (problems.isEmpty()) return null
    val listed = problems.take(MAX_LISTED)
    val rest = problems.size - listed.size
    val lines = listed.joinToString("\n") { "- ${relativize(it.path, repositoryRoots)}:${it.line} ${it.message}" }
    val tail = if (rest > 0) "\n- and $rest more" else ""

    return "The IDE reports these problems in what you changed. Fix them:\n$lines$tail\r"
}
