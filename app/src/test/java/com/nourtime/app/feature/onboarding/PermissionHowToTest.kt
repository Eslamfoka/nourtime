package com.nourtime.app.feature.onboarding

import com.nourtime.app.R
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.OemBrand
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seen on the Honor VNE-N41: its Settings screens differ from the generic instructions. */
class PermissionHowToTest {

    @Test
    fun `Honor lists Nour Time directly on the Accessibility page`() {
        assertEquals(R.string.perm_accessibility_how_honor, NourPermission.ACCESSIBILITY.how(OemBrand.HONOR_HUAWEI))
    }

    @Test
    fun `Honor opens the full list for display over other apps`() {
        assertEquals(R.string.perm_overlay_how_honor, NourPermission.OVERLAY.how(OemBrand.HONOR_HUAWEI))
    }

    @Test
    fun `other brands and other permissions keep the generic text`() {
        assertEquals(R.string.perm_accessibility_how, NourPermission.ACCESSIBILITY.how(OemBrand.SAMSUNG))
        assertEquals(R.string.perm_overlay_how, NourPermission.OVERLAY.how(null))
        assertEquals(R.string.perm_usage_how, NourPermission.USAGE_ACCESS.how(OemBrand.HONOR_HUAWEI))
    }
}
