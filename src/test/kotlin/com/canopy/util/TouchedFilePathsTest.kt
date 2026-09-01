package com.canopy.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals

class TouchedFilePathsTest {

    private fun message(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    @Test
    fun `collects paths from mutating tools`() {
        val line = message(
            """
            {"type":"assistant","message":{"content":[
              {"type":"tool_use","name":"Edit","input":{"file_path":"/ws/dmm/src/Main.kt"}},
              {"type":"tool_use","name":"Write","input":{"file_path":"/ws/one-frontend/app.tsx"}}
            ]}}
            """
        )

        assertEquals(
            listOf("/ws/dmm/src/Main.kt", "/ws/one-frontend/app.tsx"),
            touchedFilePathsIn(line)
        )
    }

    @Test
    fun `ignores reads and searches`() {
        val line = message(
            """
            {"type":"assistant","message":{"content":[
              {"type":"tool_use","name":"Read","input":{"file_path":"/ws/dmm/README.md"}},
              {"type":"tool_use","name":"Grep","input":{"path":"/ws/dmm"}},
              {"type":"tool_use","name":"Edit","input":{"file_path":"/ws/dmm/src/Main.kt"}}
            ]}}
            """
        )

        assertEquals(listOf("/ws/dmm/src/Main.kt"), touchedFilePathsIn(line))
    }

    @Test
    fun `reads the notebook path key`() {
        val line = message(
            """
            {"type":"assistant","message":{"content":[
              {"type":"tool_use","name":"NotebookEdit","input":{"notebook_path":"/ws/analysis.ipynb"}}
            ]}}
            """
        )

        assertEquals(listOf("/ws/analysis.ipynb"), touchedFilePathsIn(line))
    }

    @Test
    fun `returns nothing for plain text messages`() {
        val line = message("""{"type":"assistant","message":{"content":"just talking"}}""")

        assertEquals(emptyList(), touchedFilePathsIn(line))
    }

    @Test
    fun `skips a tool call with no path`() {
        val line = message(
            """
            {"type":"assistant","message":{"content":[
              {"type":"tool_use","name":"Edit","input":{"old_string":"a","new_string":"b"}}
            ]}}
            """
        )

        assertEquals(emptyList(), touchedFilePathsIn(line))
    }
}

class ShellCommandPathsTest {

    @Test
    fun `a repository edited through a heredoc is attributed to it`() {
        val command = "cd /Users/dev/projects/canopy && cat > src/main/kotlin/App.kt <<'EOF'\nfun main() {}\nEOF"

        assertEquals(listOf("/Users/dev/projects/canopy"), pathsInCommand(command))
    }

    @Test
    fun `every distinct path in one command counts once`() {
        val command = "cp /Users/dev/a/one.txt /Users/dev/b/two.txt && cat /Users/dev/a/one.txt"

        assertEquals(listOf("/Users/dev/a/one.txt", "/Users/dev/b/two.txt"), pathsInCommand(command))
    }

    @Test
    fun `a command naming no absolute path attributes nothing`() {
        assertEquals(emptyList<String>(), pathsInCommand("git status --short && ./gradlew test"))
    }

    @Test
    fun `a bare home directory is not a working tree`() {
        assertEquals(emptyList<String>(), pathsInCommand("ls /Users/dev"))
    }

    @Test
    fun `reading somebody else's repository does not claim it`() {
        val command = "grep -rn TODO /Users/dev/projects/other-repo/src"

        assertEquals(emptyList<String>(), pathsInCommand(command))
    }

    @Test
    fun `walking the disk to find something does not claim what it found`() {
        assertEquals(emptyList<String>(), pathsInCommand("find /Users/dev/projects -maxdepth 3 -name canopy"))
    }

    @Test
    fun `a redirection into a file is a write`() {
        assertEquals(listOf("/Users/dev/projects/canopy/note.txt"), pathsInCommand("echo hi > /Users/dev/projects/canopy/note.txt"))
    }

    @Test
    fun `stderr redirected onto stdout is not a write`() {
        assertEquals(emptyList<String>(), pathsInCommand("cat /Users/dev/projects/other/log.txt 2>&1"))
    }

    @Test
    fun `an in-place sed is a write`() {
        assertEquals(listOf("/Users/dev/projects/canopy/build.gradle.kts"), pathsInCommand("sed -i '' 's/a/b/' /Users/dev/projects/canopy/build.gradle.kts"))
    }

    @Test
    fun `committing in a repository is working in it`() {
        assertEquals(listOf("/Users/dev/projects/canopy/src"), pathsInCommand("git add /Users/dev/projects/canopy/src && git commit -m x"))
    }
}
