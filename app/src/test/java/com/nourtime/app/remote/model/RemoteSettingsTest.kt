package com.nourtime.app.remote.model

import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.settings.TimeLimits
import com.nourtime.app.data.settings.WeekendRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class RemoteSettingsTest {

    private val local = ParentSettings(
        budgetMinutes = 45,
        lockPeriodHours = 4,
        limitedApps = setOf("com.google.android.youtube"),
        allowedDuringLock = setOf("com.quran.labs.androidquran"),
        dailyResetMinute = 7 * 60,
        lockType = LockType.WHOLE_DEVICE,
        bedtime = Bedtime(enabled = true, startMinute = 21 * 60, endMinute = 6 * 60 + 30),
    )

    @Test
    fun `round-trips through the Firestore map`() {
        val r = RemoteSettings.of(local)
        val map = r.toMap(rev = 3, by = "child")
        assertEquals(3L, map["rev"])
        assertEquals("child", map["by"])
        assertEquals(r, RemoteSettings.fromMap(map))
    }

    private val weekend = WeekendRules(
        enabled = true,
        days = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
        budgetMinutes = 120,
        lockPeriodHours = 3,
        bedtime = Bedtime(enabled = true, startMinute = 23 * 60, endMinute = 9 * 60),
    )

    @Test
    fun `the weekend rules round-trip through the map and the snapshot`() {
        val r = RemoteSettings.of(local.copy(weekend = weekend))
        assertEquals(r, RemoteSettings.fromMap(r.toMap(1, "parent")))
        assertEquals(r, RemoteSettings.decode(r.encode()))
    }

    @Test
    fun `settings written before weekends existed read as weekend off, copying the normal values`() {
        val map = RemoteSettings.of(local).toMap(1, "parent") - "weekend"
        val w = RemoteSettings.fromMap(map)!!.weekend
        assertEquals(false, w.enabled)
        assertEquals(45, w.budgetMinutes)
        assertEquals(4, w.lockPeriodHours)
        assertEquals(local.bedtime, w.bedtime)
    }

    @Test
    fun `an old snapshot without weekend fields still decodes`() {
        val old = RemoteSettings.of(local).encode().split("").take(9).joinToString("")
        assertEquals(RemoteSettings.of(local).copy(weekend = WeekendRules()), RemoteSettings.decode(old))
    }

    @Test
    fun `reads Firestore numbers as longs or doubles`() {
        val map = RemoteSettings.of(local).toMap(1, "parent").toMutableMap()
        map["budgetMinutes"] = 45L
        map["lockPeriodHours"] = 4.0
        assertEquals(RemoteSettings.of(local), RemoteSettings.fromMap(map))
    }

    @Test
    fun `clamps out-of-range values like the local editors do`() {
        val map = RemoteSettings.of(local).toMap(1, "parent").toMutableMap()
        map["budgetMinutes"] = 0L
        map["lockPeriodHours"] = 99L
        val r = RemoteSettings.fromMap(map)!!
        assertEquals(TimeLimits.MIN_BUDGET_MINUTES, r.budgetMinutes)
        assertEquals(TimeLimits.MAX_LOCK_HOURS, r.lockPeriodHours)
    }

    @Test
    fun `missing or unknown required values give null`() {
        val map = RemoteSettings.of(local).toMap(1, "parent")
        assertNull(RemoteSettings.fromMap(map - "budgetMinutes"))
        assertNull(RemoteSettings.fromMap(map + ("lockType" to "SOMETHING_ELSE")))
        assertNull(RemoteSettings.fromMap(null))
    }

    @Test
    fun `an app listed as both limited and allowed stays limited`() {
        val map = RemoteSettings.of(local).toMap(1, "parent") + ("allowedDuringLock" to listOf("com.google.android.youtube"))
        assertEquals(emptySet<String>(), RemoteSettings.fromMap(map)!!.allowedDuringLock)
    }

    @Test
    fun `applying keeps the settings that are only set on the child phone`() {
        val base = local.copy(soundEnabled = false, protectSystemSettings = false)
        val remote = RemoteSettings.of(local.copy(budgetMinutes = 90))
        val merged = remote.applyTo(base)
        assertEquals(90, merged.budgetMinutes)
        assertEquals(false, merged.soundEnabled)
        assertEquals(false, merged.protectSystemSettings)
    }

    @Test
    fun `the stored snapshot round-trips`() {
        val r = RemoteSettings.of(local)
        assertEquals(r, RemoteSettings.decode(r.encode()))
        assertEquals(RemoteSettings.of(ParentSettings()), RemoteSettings.decode(RemoteSettings.of(ParentSettings()).encode()))
    }

    @Test
    fun `a damaged snapshot reads as none`() {
        assertNull(RemoteSettings.decode("garbage"))
        assertNull(RemoteSettings.decode(null))
    }

    @Test
    fun `limiting an app remotely takes it off the allowed list`() {
        val r = RemoteSettings.of(local).withAllowed("x", true).withLimited("x", true)
        assertEquals(true, "x" in r.limitedApps)
        assertEquals(false, "x" in r.allowedDuringLock)
    }

    @Test
    fun `allowing an app remotely takes it off the limited list`() {
        val r = RemoteSettings.of(local).withAllowed("com.google.android.youtube", true)
        assertEquals(false, "com.google.android.youtube" in r.limitedApps)
        assertEquals(true, "com.google.android.youtube" in r.allowedDuringLock)
    }

    @Test
    fun `switching an app off only removes it`() {
        val r = RemoteSettings.of(local).withLimited("com.google.android.youtube", false)
        assertEquals(emptySet<String>(), r.limitedApps)
        assertEquals(setOf("com.quran.labs.androidquran"), r.allowedDuringLock)
    }
}
