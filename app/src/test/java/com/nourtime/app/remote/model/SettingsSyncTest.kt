package com.nourtime.app.remote.model

import com.nourtime.app.data.settings.ParentSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Review Focus 2: edits on both phones; the parent's newer edit wins and echoes never loop. */
class SettingsSyncTest {

    private val a = RemoteSettings.of(ParentSettings(budgetMinutes = 60))
    private val b = RemoteSettings.of(ParentSettings(budgetMinutes = 30))
    private val c = RemoteSettings.of(ParentSettings(budgetMinutes = 90))

    @Test
    fun `nothing online yet uploads`() {
        assertEquals(SyncAction.UPLOAD, SettingsSync.decide(local = a, lastSynced = null, lastSyncedRev = 0, remote = null, remoteRev = 0, remoteBy = null))
    }

    @Test
    fun `nothing changed does nothing`() {
        assertEquals(SyncAction.NOTHING, SettingsSync.decide(a, a, 2, a, 2, "child"))
    }

    @Test
    fun `a local change uploads`() {
        assertEquals(SyncAction.UPLOAD, SettingsSync.decide(b, a, 2, a, 2, "child"))
    }

    @Test
    fun `a newer parent edit is applied`() {
        assertEquals(SyncAction.APPLY_REMOTE, SettingsSync.decide(a, a, 2, c, 3, "parent"))
    }

    @Test
    fun `a newer parent edit wins over an offline local change`() {
        assertEquals(SyncAction.APPLY_REMOTE, SettingsSync.decide(b, a, 2, c, 3, "parent"))
    }

    @Test
    fun `the child's own write coming back is not re-applied`() {
        assertEquals(SyncAction.NOTHING, SettingsSync.decide(b, b, 2, b, 3, "child"))
    }

    @Test
    fun `an old parent edit is not applied again`() {
        assertEquals(SyncAction.UPLOAD, SettingsSync.decide(b, a, 3, a, 3, "parent"))
    }

    @Test
    fun `the next revision is one past both sides`() {
        assertEquals(6L, SettingsSync.nextRev(lastSyncedRev = 5, remoteRev = 3))
        assertEquals(8L, SettingsSync.nextRev(lastSyncedRev = 5, remoteRev = 7))
    }
}
