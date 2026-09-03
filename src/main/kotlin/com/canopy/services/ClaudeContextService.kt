package com.canopy.services

import com.canopy.model.ContextGroup
import com.canopy.model.ContextItem
import com.canopy.model.ContextKind
import com.canopy.model.ContextLevel
import com.canopy.model.ContextSource
import com.canopy.util.ClaudePathEncoder
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ClaudeContextService(private val project: Project) {

    private data class Parsed(val size: Long, val modifiedAt: Long, val name: String?, val description: String?)

    private val parsed = ConcurrentHashMap<String, Parsed>()

    fun sources(workingDir: String?): List<ContextSource> {
        val home = Path.of(System.getProperty("user.home"), ".claude")
        val personal = LOADED_KINDS.map { ContextSource(ContextLevel.PERSONAL, it, home.resolve(it.directoryName)) }
        val projectRoot = project.basePath?.let { Path.of(it, ".claude") }
        val projectSources = projectRoot?.let { root ->
            LOADED_KINDS.map { ContextSource(ContextLevel.PROJECT, it, root.resolve(it.directoryName)) }
        }.orEmpty()
        val memoryRoot = (workingDir ?: project.basePath)?.let { ClaudePathEncoder.projectDir(it).resolve("memory") }
        val memory = memoryRoot?.let { listOf(ContextSource(ContextLevel.SESSION, ContextKind.MEMORY, it)) }.orEmpty()

        return personal + projectSources + memory
    }

    fun scan(workingDir: String?): List<ContextGroup> =
        sources(workingDir).map { ContextGroup(it, itemsIn(it)) }

    private fun itemsIn(source: ContextSource): List<ContextItem> {
        if (!Files.isDirectory(source.directory)) return emptyList()

        return try {
            Files.list(source.directory).use { stream ->
                stream.toList()
                    .mapNotNull { if (source.kind == ContextKind.SKILL) skillAt(it, source) else markdownAt(it, source) }
                    .sortedBy { it.name.lowercase() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun markdownAt(file: Path, source: ContextSource): ContextItem? {
        if (!file.toString().endsWith(".md") || !Files.isRegularFile(file)) return null
        val parsed = parse(file)

        return ContextItem(
            name = parsed.name ?: file.fileName.toString().removeSuffix(".md"),
            description = parsed.description,
            source = source,
            path = file
        )
    }

    private fun skillAt(directory: Path, source: ContextSource): ContextItem? {
        if (!Files.isDirectory(directory)) return null
        val manifest = directory.resolve("SKILL.md")
        val hasManifest = Files.isRegularFile(manifest)

        return ContextItem(
            name = directory.fileName.toString(),
            description = if (hasManifest) parse(manifest).description else null,
            source = source,
            path = if (hasManifest) manifest else directory
        )
    }

    private fun parse(file: Path): Parsed {
        val size = runCatching { Files.size(file) }.getOrDefault(0L)
        val modifiedAt = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0L)
        val cached = parsed[file.toString()]
        if (cached != null && cached.size == size && cached.modifiedAt == modifiedAt) return cached

        val head = runCatching {
            Files.lines(file).use { it.limit(FRONTMATTER_LINE_LIMIT).toList() }
        }.getOrDefault(emptyList())
        val (name, description) = frontmatterOf(head)
        val fresh = Parsed(size, modifiedAt, name, description)
        parsed[file.toString()] = fresh

        return fresh
    }

    companion object {
        private const val FRONTMATTER_LINE_LIMIT = 40L
        private val LOADED_KINDS = listOf(ContextKind.RULE, ContextKind.AGENT, ContextKind.SKILL)

        fun getInstance(project: Project): ClaudeContextService =
            project.getService(ClaudeContextService::class.java)
    }
}

private val ContextKind.directoryName: String
    get() = when (this) {
        ContextKind.RULE -> "rules"
        ContextKind.AGENT -> "agents"
        ContextKind.SKILL -> "skills"
        ContextKind.MEMORY -> "memory"
    }

internal fun frontmatterOf(lines: List<String>): Pair<String?, String?> {
    if (lines.firstOrNull()?.trim() != "---") return null to null
    var name: String? = null
    var description: String? = null

    for (line in lines.drop(1)) {
        if (line.trim() == "---") break
        if (line.startsWith("name:")) name = line.substringAfter("name:").trim().removeSurrounding("\"")
        if (line.startsWith("description:")) description = line.substringAfter("description:").trim().removeSurrounding("\"")
    }

    return name to description
}
