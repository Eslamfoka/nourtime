package com.nourtime.app.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemAutostartTest {

    @Test
    fun `aggressive OEMs and their sub-brands need the autostart step`() {
        listOf("Xiaomi", "Redmi", "POCO", "OPPO", "realme", "vivo", "HUAWEI", "HONOR", "OnePlus", "samsung").forEach {
            assertTrue(it, OemAutostart.isRelevant(it))
        }
    }

    @Test
    fun `other OEMs skip it`() {
        listOf("Google", "motorola").forEach {
            assertFalse(it, OemAutostart.isRelevant(it))
        }
    }

    @Test
    fun `sub-brands get their parent brand's instructions`() {
        assertEquals(OemBrand.XIAOMI, OemAutostart.brand("Redmi"))
        assertEquals(OemBrand.XIAOMI, OemAutostart.brand("POCO"))
        assertEquals(OemBrand.OPPO, OemAutostart.brand("realme"))
        assertEquals(OemBrand.VIVO, OemAutostart.brand("iQOO"))
        assertEquals(OemBrand.HONOR_HUAWEI, OemAutostart.brand("HONOR"))
        assertEquals(OemBrand.HONOR_HUAWEI, OemAutostart.brand("HUAWEI"))
        assertEquals(OemBrand.SAMSUNG, OemAutostart.brand("samsung"))
        assertEquals(OemBrand.ONEPLUS, OemAutostart.brand("OnePlus"))
        assertNull(OemAutostart.brand("Google"))
    }
}
