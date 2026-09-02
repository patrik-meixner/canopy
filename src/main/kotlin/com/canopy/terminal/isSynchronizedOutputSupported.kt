package com.canopy.terminal

private const val SYNCHRONIZED_OUTPUT_CLASS = "com.jediterm.terminal.emulator.SynchronizedOutput"

/** Older platforms ship an emulator without this class, and log every marker it then meets as unsupported. */
internal fun isSynchronizedOutputSupported(): Boolean =
    runCatching { Class.forName(SYNCHRONIZED_OUTPUT_CLASS, false, FilteringPtyConnector::class.java.classLoader) }.isSuccess
