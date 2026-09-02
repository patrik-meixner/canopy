package com.canopy.terminal

private const val SYNCHRONIZED_OUTPUT = "\\[\\?2026[hl]"
private const val KITTY_KEYBOARD_PUSH = "\\[>\\d+(?:;\\d+)*u"
private const val KITTY_KEYBOARD_POP = "\\[<u"
private const val MODIFY_OTHER_KEYS = "\\[>\\d+(?:;\\d+)*m"

/**
 * The kitty keyboard pop is `CSI < u`. Matching a bare `CSI u` instead swallows DECRC, which is how
 * a TUI restores a cursor it saved, so the stream loses a sequence the terminal does support.
 *
 * An emulator that batches a frame between the synchronized-output markers has to see both of
 * them: stripped, every partial frame is painted the moment it arrives, and the screen tears.
 */
internal fun unsupportedSequences(isSynchronizedOutputSupported: Boolean): Regex {
    val dropped = listOfNotNull(
        SYNCHRONIZED_OUTPUT.takeUnless { isSynchronizedOutputSupported },
        KITTY_KEYBOARD_PUSH,
        KITTY_KEYBOARD_POP,
        MODIFY_OTHER_KEYS
    )

    return Regex(dropped.joinToString("|"))
}
