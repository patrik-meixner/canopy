package com.canopy.services

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFootprintTest {

    private val root: Path = Files.createTempDirectory("canopy-footprint")

    @AfterTest
    fun cleanUp() {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    @Test
    fun `the transcript is found wherever the encoder happened to put it`() {
        val guessed = root.resolve("project-2.0").createDirectories()
        val real = root.resolve("project-2-0").createDirectories()
        real.resolve("s1.jsonl").writeText("{}")

        val found = sessionFootprint(listOf(guessed, real), root.resolve("tasks"), "s1")

        assertEquals(listOf(real.resolve("s1.jsonl")), found)
    }

    @Test
    fun `what the session stored beside its transcript goes with it`() {
        val directory = root.resolve("project").createDirectories()
        val tasks = root.resolve("tasks")
        directory.resolve("s1.jsonl").writeText("{}")
        directory.resolve("s1").createDirectories().resolve("tool-result.json").writeText("{}")
        tasks.resolve("s1").createDirectories().resolve("1.json").writeText("{}")

        val found = sessionFootprint(listOf(directory), tasks, "s1")

        assertEquals(
            listOf(directory.resolve("s1.jsonl"), directory.resolve("s1"), tasks.resolve("s1")),
            found
        )
    }

    @Test
    fun `a session with nothing on disk leaves nothing to delete`() {
        val directory = root.resolve("empty").createDirectories()

        assertEquals(emptyList(), sessionFootprint(listOf(directory), root.resolve("tasks"), "s1"))
    }

    @Test
    fun `another session's files are never in the footprint`() {
        val directory = root.resolve("shared").createDirectories()
        directory.resolve("s1.jsonl").writeText("{}")
        directory.resolve("s2.jsonl").writeText("{}")

        assertEquals(listOf(directory.resolve("s1.jsonl")), sessionFootprint(listOf(directory), root.resolve("tasks"), "s1"))
    }
}
