package com.canopy.toolwindow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionPushTest {

    @Test
    fun `submodules are pushed before the superproject`() {
        val ordered = pushOrder(listOf("/ws", "/ws/dmm", "/ws/one-frontend"), "/ws")

        assertEquals(listOf("/ws/dmm", "/ws/one-frontend", "/ws"), ordered)
    }

    @Test
    fun `without a superproject the order is left alone`() {
        val roots = listOf("/a", "/b")

        assertEquals(roots, pushOrder(roots, null))
    }

    @Test
    fun `gitlab's merge request link is picked out of the push output`() {
        val output = """
            remote:
            remote: To create a merge request for feature/x, visit:
            remote:   https://gitlab.example.com/group/repo/-/merge_requests/new?merge_request%5Bsource_branch%5D=feature%2Fx
            remote:
            To gitlab.example.com:group/repo.git
        """.trimIndent()

        assertEquals(
            "https://gitlab.example.com/group/repo/-/merge_requests/new?merge_request%5Bsource_branch%5D=feature%2Fx",
            reviewUrl(output)
        )
    }

    @Test
    fun `github's pull request link is picked out of the push output`() {
        val output = "remote: Create a pull request for 'feature/x' on GitHub by visiting:\n" +
            "remote:      https://github.com/owner/repo/pull/new/feature/x\n"

        assertEquals("https://github.com/owner/repo/pull/new/feature/x", reviewUrl(output))
    }

    @Test
    fun `an ordinary push offers no link`() {
        val output = "To github.com:owner/repo.git\n   abc1234..def5678  main -> main\n"

        assertNull(reviewUrl(output))
    }
}
