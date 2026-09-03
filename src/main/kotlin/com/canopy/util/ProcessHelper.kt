package com.canopy.util

import com.intellij.openapi.diagnostic.Logger
import java.io.File

object ProcessHelper {

    private val log = Logger.getInstance(ProcessHelper::class.java)
    private val resolvedPaths = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val EXTRA_PATHS = listOf(
        "${System.getProperty("user.home")}/.local/bin",
        "/opt/homebrew/bin",
        "/usr/local/bin"
    )

    fun augmentedEnv(): MutableMap<String, String> {
        val env = System.getenv().toMutableMap()
        val currentPath = env["PATH"] ?: "/usr/bin:/bin"
        env["PATH"] = (EXTRA_PATHS + currentPath.split(":")).distinct().joinToString(":")
        return env
    }

    data class ExecResult(val exitCode: Int, val output: String, val timedOut: Boolean = false)

    /** Never `readText()` then `waitFor()`: readText blocks until EOF, which can be never. */
    fun execWithTimeout(
        command: Array<String>,
        timeoutMs: Long,
        workDir: String? = null,
        extraEnv: Map<String, String> = emptyMap()
    ): ExecResult {
        val pb = builder(*command).redirectErrorStream(true)
        // Don't share parent's stdin — child processes that try to read would block forever.
        pb.redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
        if (workDir != null) pb.directory(java.io.File(workDir))
        if (extraEnv.isNotEmpty()) pb.environment().putAll(extraEnv)

        val process = pb.start()
        val output = StringBuilder()
        val reader = Thread({
            try {
                process.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(4096)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(output) { output.append(buf, 0, n) }
                    }
                }
            } catch (_: Exception) {
            }
        }, "Canopy-proc-reader").apply { isDaemon = true; start() }

        val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            log.warn("Canopy: command timed out after ${timeoutMs}ms: ${command.joinToString(" ")}")
            process.destroyForcibly()
            reader.join(1000)
            return ExecResult(-1, synchronized(output) { output.toString() }, timedOut = true)
        }
        reader.join(2000)
        return ExecResult(process.exitValue(), synchronized(output) { output.toString() })
    }

    fun builder(vararg command: String): ProcessBuilder {
        // ProcessBuilder finds the command on the parent JVM's PATH, not on the child environment's.
        val resolved = if (command.isNotEmpty() && !command[0].contains(File.separator)) {
            val abs = which(command[0])
            if (abs != null) arrayOf(abs, *command.drop(1).toTypedArray()) else command
        } else {
            command
        }
        return ProcessBuilder(*resolved).apply {
            environment().putAll(augmentedEnv())
        }
    }

    fun which(binary: String): String? {
        resolvedPaths[binary]?.let { return it }
        val env = augmentedEnv()
        val pathDirs = (env["PATH"] ?: "").split(":")
        for (dir in pathDirs) {
            val candidate = File(dir, binary)
            if (candidate.exists() && candidate.canExecute()) {
                log.info("Canopy: resolved '$binary' to ${candidate.absolutePath}")
                resolvedPaths[binary] = candidate.absolutePath
                return candidate.absolutePath
            }
        }
        log.warn("Canopy: '$binary' not found in any PATH directory")
        return null
    }
}
