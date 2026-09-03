package com.canopy.insight

fun turnCardKey(turn: ActivityTurn): String =
    "${turn.ordinal}:${turn.prompt.length}:${turn.filesWritten.size}:${turn.commandsRun}:${turn.lookups}"
