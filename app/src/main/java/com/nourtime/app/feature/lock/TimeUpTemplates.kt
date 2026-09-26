package com.nourtime.app.feature.lock

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.nourtime.app.R
import com.nourtime.app.core.blocking.BlockDecision
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.designsystem.component.NourPose
import com.nourtime.app.data.db.PeriodKind
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender

/** The "Time's up" designs (brief §4, §12). New themes are added here. */
enum class TemplateKind { DEFAULT, PLAY, STUDY, MEAL, SLEEP, PARENTS_ONLY }

fun templateFor(reason: BlockReason, period: PeriodKind?): TemplateKind = when (reason) {
    BlockReason.BEDTIME -> TemplateKind.SLEEP
    BlockReason.SYSTEM_SETTINGS -> TemplateKind.PARENTS_ONLY
    BlockReason.PROTECTION -> TemplateKind.DEFAULT
    BlockReason.TIME_UP -> when (period) {
        PeriodKind.STUDY -> TemplateKind.STUDY
        PeriodKind.PLAY -> TemplateKind.PLAY
        PeriodKind.MEAL -> TemplateKind.MEAL
        PeriodKind.SLEEP -> TemplateKind.SLEEP
        null -> TemplateKind.DEFAULT
    }
}

/** Everything the lock overlay needs to draw one frame. */
data class LockScreenState(
    val decision: BlockDecision,
    val template: TemplateKind,
    val ageGroup: AgeGroup,
    val gender: ChildGender,
    /** Until the refill (time up) or the end of bedtime; null when there's nothing to count down. */
    val countdownMs: Long?,
    val soundEnabled: Boolean,
    /** Apps the parent allows during lock periods and bedtime, offered as buttons on the screen. */
    val allowedApps: List<AllowedApp> = emptyList(),
)

data class AllowedApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/** One gendered child message (brief: every child message has masculine and feminine forms). */
data class Gendered(@StringRes val boy: Int, @StringRes val girl: Int) {
    constructor(@StringRes both: Int) : this(both, both)

    fun pick(gender: ChildGender) = if (gender == ChildGender.GIRL) girl else boy
}

data class Activity(@StringRes val label: Int)

data class TemplateStyle(
    val background: List<Color>,
    /** Light text on a dark background (sleep). */
    val dark: Boolean,
    val pose: NourPose,
    val title: Gendered,
    val activities: List<Activity>,
)

private val playActivities = listOf(Activity(R.string.act_drawing), Activity(R.string.act_blocks), Activity(R.string.act_ball))

fun styleFor(kind: TemplateKind): TemplateStyle = when (kind) {
    TemplateKind.PLAY -> TemplateStyle(
        background = listOf(Color(0xFFBFE6FF), Color(0xFFE9F7FF)),
        dark = false,
        pose = NourPose.PLAYING,
        title = Gendered(R.string.tu_play_title),
        activities = playActivities,
    )
    TemplateKind.STUDY -> TemplateStyle(
        background = listOf(Color(0xFFFFF8EC), Color(0xFFDCEBFF)),
        dark = false,
        pose = NourPose.STUDYING,
        title = Gendered(R.string.tu_study_title),
        activities = listOf(Activity(R.string.act_story), Activity(R.string.act_puzzle), Activity(R.string.act_coloring)),
    )
    TemplateKind.MEAL -> TemplateStyle(
        background = listOf(Color(0xFFFFD9B0), Color(0xFFFFB98A)),
        dark = false,
        pose = NourPose.EATING,
        title = Gendered(R.string.tu_meal_title),
        activities = listOf(Activity(R.string.act_hands), Activity(R.string.act_table), Activity(R.string.act_water)),
    )
    TemplateKind.SLEEP -> TemplateStyle(
        background = listOf(Color(0xFF141C33), Color(0xFF1E2A4A)),
        dark = true,
        pose = NourPose.SLEEPING,
        title = Gendered(R.string.tu_sleep_title_m, R.string.tu_sleep_title_f),
        activities = emptyList(),
    )
    TemplateKind.PARENTS_ONLY -> TemplateStyle(
        background = listOf(Color(0xFFFFF3D1), Color(0xFFFFF8EC)),
        dark = false,
        pose = NourPose.HAPPY,
        title = Gendered(R.string.tu_parents_title_m, R.string.tu_parents_title_f),
        activities = emptyList(),
    )
    TemplateKind.DEFAULT -> TemplateStyle(
        background = listOf(Color(0xFFFFE7A8), Color(0xFFFFF3D1)),
        dark = false,
        pose = NourPose.WAVING,
        title = Gendered(R.string.tu_default_title),
        activities = playActivities,
    )
}

/** Ages 10–12 get calmer colours (brief §12): the palette is softened toward cream. */
fun TemplateStyle.forAge(age: AgeGroup): TemplateStyle =
    if (age != AgeGroup.AGES_10_12 || dark) this
    else copy(background = background.map { lerp(it, Color(0xFFFFFBF4), 0.55f) })

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
