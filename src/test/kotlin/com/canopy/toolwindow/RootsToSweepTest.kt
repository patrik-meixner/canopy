package com.canopy.toolwindow

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RootsToSweepTest {

    private val still = RootFingerprint(gitState = 1, ledgerModified = 2, ledgerSize = 3, newestWrite = 4, existingWrites = 1)
    private val moved = still.copy(newestWrite = 5)

    @Test
    fun `a repository nothing moved in is not swept again`() {
        assertEquals(emptySet(), rootsToSweep(mapOf("/ws" to still), mapOf("/ws" to still), isForced = false))
    }

    @Test
    fun `only the repository that moved is swept`() {
        val swept = mapOf("/ws" to still, "/ws/dmm" to still)

        assertEquals(setOf("/ws/dmm"), rootsToSweep(mapOf("/ws" to still, "/ws/dmm" to moved), swept, isForced = false))
    }

    @Test
    fun `a repository never swept is swept`() {
        assertEquals(setOf("/ws"), rootsToSweep(mapOf("/ws" to still), emptyMap(), isForced = false))
    }

    @Test
    fun `an explicit refresh sweeps everything`() {
        assertEquals(setOf("/ws"), rootsToSweep(mapOf("/ws" to still), mapOf("/ws" to still), isForced = true))
    }

    @Test
    fun `a file the session wrote moves its repository's fingerprint`() {
        val root = Files.createTempDirectory("canopy-root")
        val file = Files.writeString(root.resolve("App.kt"), "x")
        val ledger = root.resolve("missing.ledger")
        val before = RootFingerprint.of(root.toString(), ledger, setOf(file.toString()))

        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000))

        assertNotEquals(before, RootFingerprint.of(root.toString(), ledger, setOf(file.toString())))
    }

    @Test
    fun `a file written in another repository leaves this one's fingerprint alone`() {
        val root = Files.createTempDirectory("canopy-root")
        val elsewhere = Files.writeString(Files.createTempDirectory("canopy-other").resolve("App.kt"), "x")
        val ledger = root.resolve("missing.ledger")
        val before = RootFingerprint.of(root.toString(), ledger, setOf(elsewhere.toString()))

        Files.setLastModifiedTime(elsewhere, FileTime.fromMillis(Files.getLastModifiedTime(elsewhere).toMillis() + 5_000))

        assertEquals(before, RootFingerprint.of(root.toString(), ledger, setOf(elsewhere.toString())))
    }
}
