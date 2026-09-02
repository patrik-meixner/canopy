package com.canopy.services

import java.nio.file.Path

private val BUILD_OUTPUT = listOf(
    "/build/", "/out/", "/dist/", "/target/", "/node_modules/",
    "/.gradle/", "/.kotlin/", "/.idea/", "/.git/objects/", "/.git/logs/"
)

private val CLAUDE_HOME = Path.of(System.getProperty("user.home"), ".claude").toString() + "/"

/**
 * Whether a changed file could change what a review shows.
 *
 * Every review panel refreshes by running git across every repository a session touched - around
 * five processes each, and a session routinely spans several. Hanging that on any file event at all
 * meant a build's output triggered the whole sweep, and so did the agent's own transcript: the VFS
 * reports it on every return to the window, which is the moment the terminal is trying to redraw.
 */
fun changesWorkingTree(path: String, claudeHome: String = CLAUDE_HOME): Boolean =
    !path.startsWith(claudeHome) && BUILD_OUTPUT.none { it in path }
