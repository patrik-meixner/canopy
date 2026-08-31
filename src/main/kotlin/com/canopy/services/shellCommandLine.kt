package com.canopy.services

/**
 * The agent's command line as a login shell would be given it.
 *
 * Started as the pty's own process the agent renders inline: no alternate screen, and no mouse, so
 * neither selection nor the clickable parts of its interface work. Started as a child of the shell
 * it behaves as it does in any terminal, which is the only arrangement observed to work.
 */
fun shellCommandLine(command: List<String>): String = command.joinToString(" ", transform = ::quoted)

private fun quoted(argument: String): String {
    if (argument.isNotEmpty() && argument.all { it.isLetterOrDigit() || it in SAFE }) return argument

    return "'" + argument.replace("'", "'\\''") + "'"
}

private const val SAFE = "-_./=:@+,"
