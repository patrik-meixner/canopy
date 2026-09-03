package com.canopy.services

fun shellCommandLine(command: List<String>): String = command.joinToString(" ", transform = ::quoted)

private fun quoted(argument: String): String {
    if (argument.isNotEmpty() && argument.all { it.isLetterOrDigit() || it in SAFE }) return argument

    return "'" + argument.replace("'", "'\\''") + "'"
}

private const val SAFE = "-_./=:@+,"
