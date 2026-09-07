package com.canopy.util

import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleEnvTest {

    @Test
    fun `a shell inheriting no locale is told the bytes are UTF-8`() {
        assertEquals(mapOf("LC_CTYPE" to "UTF-8"), localeEnv(emptyMap(), "UTF-8"))
    }

    @Test
    fun `a locale the user already chose is never overwritten`() {
        assertEquals(emptyMap(), localeEnv(mapOf("LC_CTYPE" to "cs_CZ.ISO8859-2"), "UTF-8"))
        assertEquals(emptyMap(), localeEnv(mapOf("LANG" to "cs_CZ.UTF-8"), "UTF-8"))
        assertEquals(emptyMap(), localeEnv(mapOf("LC_ALL" to "C"), "UTF-8"))
    }

    @Test
    fun `a locale variable set to nothing is not a choice`() {
        assertEquals(mapOf("LC_CTYPE" to "UTF-8"), localeEnv(mapOf("LANG" to ""), "UTF-8"))
    }
}
