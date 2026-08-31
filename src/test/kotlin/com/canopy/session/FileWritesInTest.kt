package com.canopy.session

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FileWritesInTest {

    private fun record(json: String): JsonObject = Gson().fromJson(json, JsonObject::class.java)

    private fun assistant(vararg blocks: String) = record(
        """{"type":"assistant","message":{"content":[${blocks.joinToString(",")}]}}"""
    )

    @Test
    fun `a whole-file write is read with its content`() {
        val writes = fileWritesIn(
            assistant("""{"type":"tool_use","name":"Write","input":{"file_path":"/a","content":"body"}}""")
        )

        assertEquals(listOf(FileWrite.Whole("/a", "body")), writes)
    }

    @Test
    fun `a replacement is read with both sides`() {
        val writes = fileWritesIn(
            assistant("""{"type":"tool_use","name":"Edit","input":{"file_path":"/a","old_string":"x","new_string":"y"}}""")
        )

        assertEquals(listOf(FileWrite.Replace("/a", "x", "y")), writes)
    }

    @Test
    fun `a replacement missing a side is dropped rather than half read`() {
        val writes = fileWritesIn(
            assistant("""{"type":"tool_use","name":"Edit","input":{"file_path":"/a","old_string":"x"}}""")
        )

        assertEquals(emptyList<FileWrite>(), writes)
    }

    @Test
    fun `a tool that does not write files is ignored`() {
        val writes = fileWritesIn(
            assistant("""{"type":"tool_use","name":"Read","input":{"file_path":"/a"}}""")
        )

        assertEquals(emptyList<FileWrite>(), writes)
    }

    @Test
    fun `writes keep the order the agent made them in`() {
        val writes = fileWritesIn(
            assistant(
                """{"type":"tool_use","name":"Write","input":{"file_path":"/a","content":"first"}}""",
                """{"type":"tool_use","name":"Edit","input":{"file_path":"/a","old_string":"first","new_string":"second"}}""",
            )
        )

        assertEquals(listOf(FileWrite.Whole("/a", "first"), FileWrite.Replace("/a", "first", "second")), writes)
    }

    @Test
    fun `what the human said carries no writes`() {
        assertEquals(emptyList<FileWrite>(), fileWritesIn(record("""{"type":"user","message":{"content":[]}}""")))
    }
}
