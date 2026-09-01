package com.canopy.services

private val BUILD_OUTPUT = listOf(
    "/build/", "/out/", "/dist/", "/target/", "/node_modules/",
    "/.gradle/", "/.kotlin/", "/.idea/", "/.git/objects/", "/.git/logs/"
)

/**
 * Whether a changed file could change what a review shows.
 *
 * Every review panel refreshes by running git across every repository a session touched - around
 * five processes each, and a session routinely spans several. Hanging that on any file event at all
 * meant a build's output triggered the whole sweep. None of these paths is ever a change to review:
 * they are what builds and the IDE write, and git is not tracking them.
 */
fun changesWorkingTree(path: String): Boolean = BUILD_OUTPUT.none { it in path }
