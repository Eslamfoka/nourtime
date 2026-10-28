package com.nourtime.app.feature.lock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.nourtime.app.R
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.data.db.PeriodKind
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUpTemplatesTest {

    @Test
    fun `template follows the reason and the period of the day`() {
        assertEquals(TemplateKind.SLEEP, templateFor(BlockReason.BEDTIME, PeriodKind.PLAY))
        assertEquals(TemplateKind.STUDY, templateFor(BlockReason.TIME_UP, PeriodKind.STUDY))
        assertEquals(TemplateKind.DEFAULT, templateFor(BlockReason.TIME_UP, null))
        assertEquals(TemplateKind.PARENTS_ONLY, templateFor(BlockReason.SYSTEM_SETTINGS, PeriodKind.MEAL))
        assertEquals(TemplateKind.PROTECTION_OFF, templateFor(BlockReason.PROTECTION, PeriodKind.MEAL))
    }

    @Test
    fun `protection off says so on every age's layout, not well done`() {
        val style = styleFor(TemplateKind.PROTECTION_OFF)
        assertEquals(R.string.tu_protection_off_title, style.title.pick(ChildGender.GIRL))
        assertEquals(R.string.tu_protection_off_body, style.body?.pick(ChildGender.BOY))
        assertEquals(emptyList<Activity>(), style.activities)
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

    @Test
    fun `for parents is readable on every template and age`() {
        // WCAG AA for small text: 4.5:1, against both ends of each background gradient.
        TemplateKind.entries.forEach { kind ->
            AgeGroup.entries.forEach { age ->
                val style = styleFor(kind).forAge(age)
                val text = if (style.dark) NourPalette.Cream else NourPalette.Navy
                style.background.forEach { background ->
                    val drawn = text.copy(alpha = PARENTS_BUTTON_ALPHA).compositeOver(background)
                    val ratio = contrast(drawn, background)
                    assertTrue("$kind $age: $ratio", ratio >= 4.5)
                }
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }
}
