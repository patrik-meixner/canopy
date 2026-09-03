package com.canopy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PathsInCommandTest {

    @Test
    fun `a read beside a write in the same command is not a write`() {
        val command = """
            cat /Users/me/other-repo/notes.md
            mkdir -p /tmp/scratch/out
        """.trimIndent()

        assertEquals(listOf("/tmp/scratch/out"), pathsInCommand(command))
    }

    @Test
    fun `only what follows the redirection was written`() {
        assertEquals(
            listOf("/tmp/scratch/out.txt"),
            pathsInCommand("grep -c foo /Users/me/repo/a.kt > /tmp/scratch/out.txt")
        )
    }

    @Test
    fun `a heredoc written after a cd is attributed to that directory`() {
        val command = """
            cd /Users/me/canopy; cat > src/main/kotlin/X.kt <<'KT'
            package x
            KT
        """.trimIndent()

        assertEquals(listOf("/Users/me/canopy"), pathsInCommand(command))
    }

    @Test
    fun `a cd followed only by reads names nothing`() {
        assertEquals(emptyList<String>(), pathsInCommand("cd /Users/me/repo && git log -3 && cat README.md"))
    }

    @Test
    fun `a command that writes nothing names nothing`() {
        assertEquals(emptyList<String>(), pathsInCommand("ls -la /Users/me/repo; grep -rn foo /Users/me/repo/src"))
    }

    @Test
    fun `stderr to stdout is not a redirection into a file`() {
        assertEquals(emptyList<String>(), pathsInCommand("./gradlew test 2>&1 | tail -5"))
    }

    @Test
    fun `the session that motivated this reads one workspace and writes another`() {
        val command = """
            cd /Users/me/canopy; python3 - <<'PY'
            p='src/main/kotlin/com/canopy/services/X.kt'
            open(p,'w').write('x')
            PY
            sed -n 1,20p /Users/me/ataccama/rdm-agentic-workspace/.idea/workspace.xml
            mkdir -p /tmp/scratch/probe
        """.trimIndent()

        assertEquals(listOf("/Users/me/canopy", "/tmp/scratch/probe"), pathsInCommand(command))
    }

    @Test
    fun `a cd followed only by reads is not claimed by a write elsewhere in the command`() {
        val command = "mkdir -p /tmp/scratch/jt && cd /Users/me/other && git rev-parse --show-toplevel && git worktree list"

        assertEquals(listOf("/tmp/scratch/jt"), pathsInCommand(command))
    }

    @Test
    fun `listing worktrees is reading them`() {
        assertEquals(emptyList<String>(), pathsInCommand("for R in /Users/me/a /Users/me/b; do git -C \$R worktree list; done"))
    }

    @Test
    fun `a write that names its own path does not claim the directory it was run from`() {
        assertEquals(listOf("/tmp/scratch/out.txt"), pathsInCommand("cd /Users/me/repo && echo x > /tmp/scratch/out.txt"))
    }

    @Test
    fun `committing through -C names the repository`() {
        assertEquals(listOf("/Users/me/repo"), pathsInCommand("git -C /Users/me/repo add -A && git -C /Users/me/repo commit -m x"))
    }

    @Test
    fun `a second cd ends the scope of the first`() {
        assertEquals(listOf("/Users/me/b"), pathsInCommand("cd /Users/me/a && ls; cd /Users/me/b && sed -i '' s/x/y/ f.txt"))
    }
}
