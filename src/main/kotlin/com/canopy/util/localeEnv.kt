package com.canopy.util

private val LOCALE_VARIABLES = listOf("LC_ALL", "LC_CTYPE", "LANG")

/**
 * A process with no locale falls back to the platform's legacy encoding, so UTF-8 it is handed
 * arrives as MacRoman - visible the moment anything copies Czech text out of a session.
 */
fun localeEnv(existing: Map<String, String>, charset: String): Map<String, String> {
    if (LOCALE_VARIABLES.any { existing[it]?.isNotBlank() == true }) return emptyMap()

    return mapOf("LC_CTYPE" to charset)
}
