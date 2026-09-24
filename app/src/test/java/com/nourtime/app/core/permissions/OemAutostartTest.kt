package com.nourtime.app.core.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemAutostartTest {

    @Test
    fun `aggressive OEMs and their sub-brands need the autostart step`() {
        listOf("Xiaomi", "Redmi", "POCO", "OPPO", "realme", "vivo", "HUAWEI", "HONOR", "OnePlus").forEach {
            assertTrue(it, OemAutostart.isRelevant(it))
        }
    }

    @Test
    fun `other OEMs skip it`() {
        listOf("samsung", "Google", "motorola").forEach {
            assertFalse(it, OemAutostart.isRelevant(it))
        }
    }
}
