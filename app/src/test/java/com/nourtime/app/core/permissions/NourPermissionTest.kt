package com.nourtime.app.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class NourPermissionTest {

    @Test
    fun `only device admin and battery can be skipped during setup`() {
        // Without Accessibility, usage access, the overlay or the notification Nour Time can't detect,
        // lock or stay visible (Play policy); the other two only make it harder to stop.
        assertEquals(
            setOf(NourPermission.DEVICE_ADMIN, NourPermission.BATTERY),
            NourPermission.entries.filter { it.skippable }.toSet(),
        )
    }
}
