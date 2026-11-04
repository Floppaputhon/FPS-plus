package com.redmagic.fps

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleSupportTest {
    @Test fun freshInstallAndUnsupportedCodesDefaultToEnglish() {
        assertEquals("en", LocaleSupport.resolve(null))
        assertEquals("en", LocaleSupport.resolve("unsupported"))
    }

    @Test fun allFiveChoicesRemainSelectable() {
        assertEquals(listOf("en", "ru", "es", "de", "ja"), LocaleSupport.supported)
        LocaleSupport.supported.forEach { assertEquals(it, LocaleSupport.resolve(it)) }
        assertEquals(LocaleSupport.supported.size, LocaleSupport.names.size)
    }
}
