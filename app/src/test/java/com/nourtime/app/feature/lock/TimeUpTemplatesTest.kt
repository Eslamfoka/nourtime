package com.nourtime.app.feature.lock

import com.nourtime.app.R
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.data.db.PeriodKind
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimeUpTemplatesTest {

    @Test
    fun `template follows the reason and the period of the day`() {
        assertEquals(TemplateKind.SLEEP, templateFor(BlockReason.BEDTIME, PeriodKind.PLAY))
        assertEquals(TemplateKind.STUDY, templateFor(BlockReason.TIME_UP, PeriodKind.STUDY))
        assertEquals(TemplateKind.DEFAULT, templateFor(BlockReason.TIME_UP, null))
        assertEquals(TemplateKind.PARENTS_ONLY, templateFor(BlockReason.SYSTEM_SETTINGS, PeriodKind.MEAL))
        assertEquals(TemplateKind.DEFAULT, templateFor(BlockReason.PROTECTION, PeriodKind.MEAL))
    }

    @Test
    fun `sleep message is gendered`() {
        val title = styleFor(TemplateKind.SLEEP).title
        assertEquals(R.string.tu_sleep_title_m, title.pick(ChildGender.BOY))
        assertEquals(R.string.tu_sleep_title_f, title.pick(ChildGender.GIRL))
    }

    @Test
    fun `older children get calmer colours, except at night`() {
        val play = styleFor(TemplateKind.PLAY)
        assertNotEquals(play.background, play.forAge(AgeGroup.AGES_10_12).background)
        assertEquals(play.background, play.forAge(AgeGroup.AGES_3_6).background)
        val sleep = styleFor(TemplateKind.SLEEP)
        assertEquals(sleep.background, sleep.forAge(AgeGroup.AGES_10_12).background)
    }
}
