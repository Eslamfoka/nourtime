package com.nourtime.app.remote.model

import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LearningSettings
import com.nourtime.app.core.learning.VoicePack
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ParentSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Learning Hub on the parent's phone (roadmap Task 3): settings, voices, minutes box, break. */
class RemoteLearningSyncTest {

    private val hub = LearningSettings(enabled = false, minutesPerLevel = 10, dailyMaxMinutes = 30)
    private val voices = mapOf(LearnLanguage.ARABIC to VoicePack.ARABIC_EGYPTIAN)
    private val r = RemoteSettings.of(ParentSettings(), hub, voices)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.learning() = (this["learning"] as Map<String, Any?>).toMutableMap()

    @Test
    fun `hub settings and voices round-trip through the map and the snapshot`() {
        assertEquals(r, RemoteSettings.fromMap(r.toMap(2, "parent")))
        assertEquals(r, RemoteSettings.decode(r.encode()))
    }

    @Test
    fun `every language gets a voice, the default when none was chosen`() {
        assertEquals(VoicePack.ARABIC_EGYPTIAN, r.voices[LearnLanguage.ARABIC])
        assertEquals(VoicePack.defaultFor(LearnLanguage.ENGLISH), r.voices[LearnLanguage.ENGLISH])
    }

    @Test
    fun `settings written before the hub was synced read as the hub defaults`() {
        val old = RemoteSettings.fromMap(r.toMap(1, "child") - "learning")!!
        assertEquals(LearningSettings(), old.learning)
        assertEquals(RemoteSettings.completeVoices(emptyMap()), old.voices)
    }

    @Test
    fun `a snapshot from before the hub was synced still decodes, with the defaults`() {
        val fields = r.encode().split(RemoteSettings.FIELD_SEP)
        val old = RemoteSettings.decode(fields.take(16).joinToString(RemoteSettings.FIELD_SEP))!!
        assertEquals(LearningSettings(), old.learning)
        assertEquals(r.budgetMinutes, old.budgetMinutes)
    }

    @Test
    fun `out-of-range minutes are clamped and unknown voices fall back to the default`() {
        val map = r.toMap(1, "parent").toMutableMap()
        val learning = map.learning()
        learning["minutesPerLevel"] = 0L
        learning["dailyMaxMinutes"] = 9_999L
        learning["voices"] = mapOf("ar" to "ENGLISH_BRITISH", "en" to "KLINGON")
        map["learning"] = learning
        val parsed = RemoteSettings.fromMap(map)!!
        assertEquals(LearningSettings.MINUTES_PER_LEVEL_RANGE.first, parsed.learning.minutesPerLevel)
        assertEquals(LearningSettings.DAILY_MAX_RANGE.last, parsed.learning.dailyMaxMinutes)
        assertEquals(RemoteSettings.completeVoices(emptyMap()), parsed.voices)
    }

    @Test
    fun `choosing a voice on the parent's phone changes only that language`() {
        val changed = r.copy(voices = r.voices + (VoicePack.ENGLISH_BRITISH.language to VoicePack.ENGLISH_BRITISH))
        assertEquals(VoicePack.ARABIC_EGYPTIAN, changed.voices[LearnLanguage.ARABIC])
        assertEquals(VoicePack.ENGLISH_BRITISH, changed.voices[LearnLanguage.ENGLISH])
        assertEquals(changed, RemoteSettings.fromMap(changed.toMap(3, "parent")))
    }

    @Test
    fun `the minutes box round-trips and earned minutes count only on their day`() {
        val day = LocalDate.of(2026, 10, 5)
        val l = RemoteLearning.of(LearningState(bankMinutes = 10, earnedDay = day, earnedMinutes = 15))
        val back = RemoteLearning.fromMap(l.toMap())!!
        assertEquals(l, back)
        assertEquals(15, back.earnedOn(day))
        assertEquals(0, back.earnedOn(day.plusDays(1)))
        assertNull(RemoteLearning.fromMap(null))
    }

    private fun status(pending: Long) =
        TimerStatus(TimerPhase.AVAILABLE, 5 * 60_000L, 3_600_000, 0, emptySet(), DetectionSource.ACCESSIBILITY, lockPendingMs = pending)

    @Test
    fun `a learning break reaches the parent at once, with the waiting lock`() {
        assertTrue(StatusThrottle.shouldUpload(status(0), status(3_600_000), sinceLastMs = 1_000))
        assertEquals(3_600_000L, RemoteStatus.fromMap(statusMap(status(3_600_000)), updatedAtMs = 1)!!.lockPendingMs)
    }
}
