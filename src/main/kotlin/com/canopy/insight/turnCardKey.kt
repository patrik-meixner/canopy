package com.canopy.insight

/** A turn redraws when its prompt, its files or how far it got have changed, and not otherwise. */
fun turnCardKey(turn: ActivityTurn): String =
    "${turn.ordinal}:${turn.prompt.length}:${turn.filesWritten.size}:${turn.commandsRun}:${turn.lookups}"
