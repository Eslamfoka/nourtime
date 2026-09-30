package com.nourtime.app.feature.learning

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered
import com.nourtime.app.feature.setup.durationText

/** Look of each game: its tile icon and accent color. */
internal data class GameLook(val emoji: String, val accent: Color, val title: Int, val hint: Int)

internal fun lookOf(game: GameId) = when (game) {
    GameId.MATH -> GameLook("🔢", NourPalette.Mint, R.string.learn_game_math, R.string.learn_game_math_hint)
    GameId.LETTERS -> GameLook("🔤", NourPalette.Coral, R.string.learn_game_letters, R.string.learn_game_letters_hint)
    GameId.CONNECT -> GameLook("✏️", Color(0xFF7E8CE0), R.string.learn_game_connect, R.string.learn_game_connect_hint)
    GameId.COLORING -> GameLook("🎨", NourPalette.GoldDeep, R.string.learn_game_coloring, R.string.learn_game_coloring_hint)
}

internal val HubBackground = Brush.verticalGradient(listOf(NourPalette.Cream, NourPalette.GoldLight))

/**
 * The Learning Hub (shown instead of the Time's up screen while the child plays). [onClose] returns
 * to the Time's up screen.
 */
@Composable
fun LearningHub(controller: LearningHubController, gender: ChildGender, onClose: () -> Unit) {
    val screen by controller.screen.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    val speaker = rememberSpeaker()
    CompositionLocalProvider(LocalSpeaker provides speaker) {
        Box(Modifier.fillMaxSize().background(HubBackground)) {
            AnimatedContent(
                targetState = screen,
                contentKey = { it::class to (it as? HubScreen.Playing)?.level },
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "hub",
            ) { s ->
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (s) {
                        HubScreen.Menu -> HubMenu(controller, state, gender, onClose)
                        is HubScreen.Levels -> LevelPicker(controller, state, s.game, gender)
                        is HubScreen.Playing -> QuestionScreen(controller, state, s, gender)
                        is HubScreen.Connecting -> ConnectScreen(controller, state, s, gender, controller.age)
                        is HubScreen.Coloring -> ColoringScreen(controller, state, s, gender)
                        is HubScreen.Done -> DoneScreen(controller, state, s, gender)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HubTopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = NourPalette.Navy)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = NourPalette.Navy,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            maxLines = 1,
        )
        trailing()
    }
}

@Composable
private fun BankChip(minutes: Int) {
    if (minutes <= 0) return
    Surface(shape = CircleShape, color = NourPalette.Mint) {
        Text(
            "⏱ " + durationText(minutes),
            style = MaterialTheme.typography.labelLarge,
            color = NourPalette.Navy,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ColumnScope.HubMenu(controller: LearningHubController, state: LearningState?, gender: ChildGender, onClose: () -> Unit) {
    val settings = state?.settings
    val earning = controller.rewards && settings != null && settings.dailyMaxMinutes > 0
    HubTopBar(stringResource(R.string.learn_hub_title), onClose)
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NourStar(Modifier.size(96.dp))
        Text(
            stringResource(
                if (earning) Gendered(R.string.learn_hub_subtitle_m, R.string.learn_hub_subtitle_f).pick(gender)
                else Gendered(R.string.learn_hub_subtitle_fun_m, R.string.learn_hub_subtitle_fun_f).pick(gender),
            ),
            style = MaterialTheme.typography.titleMedium,
            color = NourPalette.Navy,
            textAlign = TextAlign.Center,
        )
        if (!controller.rewards) {
            Text(stringResource(R.string.learn_preview_note), style = MaterialTheme.typography.bodySmall, color = NourPalette.Muted)
        }
        val bank = state?.bankMinutes ?: 0
        if (controller.rewards && bank > 0) {
            Surface(shape = RoundedCornerShape(24.dp), color = NourPalette.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.learn_bank, durationText(bank)), style = MaterialTheme.typography.titleLarge, color = NourPalette.Navy)
                    NourPrimaryButton(
                        stringResource(Gendered(R.string.learn_use_minutes_m, R.string.learn_use_minutes_f).pick(gender)),
                        controller::useMinutes,
                    )
                }
            }
        }
        GameId.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { game -> GameTile(game, game in PLAYABLE_GAMES, { controller.openGame(game) }, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GameTile(game: GameId, playable: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val look = lookOf(game)
    Surface(
        onClick = onClick,
        enabled = playable,
        shape = RoundedCornerShape(28.dp),
        color = NourPalette.White,
        shadowElevation = if (playable) 3.dp else 0.dp,
        modifier = modifier.aspectRatio(0.9f).alpha(if (playable) 1f else 0.55f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).background(look.accent.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Text(look.emoji, fontSize = 40.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(look.title), style = MaterialTheme.typography.titleMedium, color = NourPalette.Navy, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Text(
                stringResource(if (playable) look.hint else R.string.learn_coming_soon),
                style = MaterialTheme.typography.bodySmall,
                color = NourPalette.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.LevelPicker(controller: LearningHubController, state: LearningState?, game: GameId, gender: ChildGender) {
    val look = lookOf(game)
    val numerals = controller.numerals(state)
    HubTopBar(stringResource(look.title), { controller.back() }) { BankChip(if (controller.rewards) state?.bankMinutes ?: 0 else 0) }
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (game) {
            GameId.MATH, GameId.CONNECT -> OptionRow(stringResource(R.string.learn_numerals), NumeralStyle.entries, numerals, { it.format(123) }, controller::setNumerals)
            GameId.LETTERS -> OptionRow(
                stringResource(R.string.learn_language),
                LearnLanguage.entries,
                controller.lettersLanguage(state),
                { if (it == LearnLanguage.ARABIC) "عربي" else "English" },
                controller::setLettersLanguage,
            )
            else -> Unit
        }
        val unlocked = controller.unlocked(state, game)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(levelCount(game)) { level ->
                LevelButton(
                    label = numerals.format(level + 1),
                    stars = controller.stars(state, game, level),
                    locked = level > unlocked,
                    current = controller.rewards && level == unlocked,
                    accent = look.accent,
                    onClick = { controller.play(game, level) },
                )
            }
        }
    }
}

@Composable
private fun <T> OptionRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = NourPalette.Navy)
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.titleMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = NourPalette.White,
                    labelColor = NourPalette.Navy,
                    selectedContainerColor = NourPalette.Navy,
                    selectedLabelColor = NourPalette.Cream,
                ),
            )
        }
    }
}

@Composable
private fun LevelButton(label: String, stars: Int, locked: Boolean, current: Boolean, accent: Color, onClick: () -> Unit) {
    val description = if (locked) stringResource(R.string.learn_level_locked, label) else stringResource(R.string.learn_level_stars, label, stars)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Surface(
            onClick = onClick,
            enabled = !locked,
            shape = CircleShape,
            color = when {
                locked -> NourPalette.White.copy(alpha = 0.6f)
                current -> accent
                else -> NourPalette.White
            },
            shadowElevation = if (locked) 0.dp else 2.dp,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (locked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = NourPalette.Muted)
                } else {
                    Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
                }
            }
        }
        StarRow(stars, Modifier.padding(top = 4.dp), size = 18)
    }
}

@Composable
internal fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Int = 18) {
    Row(modifier) {
        repeat(3) { i ->
            Icon(
                if (i < stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = null,
                tint = if (i < stars) NourPalette.GoldDeep else NourPalette.Muted.copy(alpha = 0.5f),
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.DoneScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Done, gender: ChildGender) {
    val numerals = controller.numerals(state)
    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        NourStar(Modifier.size(120.dp))
        Text(
            stringResource(Gendered(R.string.learn_done_title_m, R.string.learn_done_title_f).pick(gender)),
            style = MaterialTheme.typography.headlineMedium,
            color = NourPalette.Navy,
        )
        var shown by remember { mutableStateOf(0) }
        LaunchedEffect(s) {
            for (i in 1..s.stars) {
                kotlinx.coroutines.delay(250)
                shown = i
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                AnimatedContent(i < shown, transitionSpec = { scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith fadeOut() }, label = "star") { on ->
                    Icon(
                        if (on) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = null,
                        tint = if (on) NourPalette.GoldDeep else NourPalette.Muted.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Text(stringResource(R.string.learn_stars, s.stars), style = MaterialTheme.typography.bodyMedium, color = NourPalette.Muted)
        val settings = state?.settings
        when {
            s.earnedMinutes > 0 -> Text(
                stringResource(Gendered(R.string.learn_earned_m, R.string.learn_earned_f).pick(gender), durationText(s.earnedMinutes)),
                style = MaterialTheme.typography.titleLarge,
                color = NourPalette.MintDeep,
                textAlign = TextAlign.Center,
            )
            !controller.rewards || settings == null || !settings.enabled || settings.dailyMaxMinutes == 0 -> Unit
            s.dailyMaxReached -> Text(stringResource(R.string.learn_daily_max_reached), style = MaterialTheme.typography.bodyLarge, color = NourPalette.Navy, textAlign = TextAlign.Center)
            else -> Text(
                stringResource(Gendered(R.string.learn_need_stars_m, R.string.learn_need_stars_f).pick(gender)),
                style = MaterialTheme.typography.bodyLarge,
                color = NourPalette.Navy,
                textAlign = TextAlign.Center,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        val bank = state?.bankMinutes ?: 0
        if (controller.rewards && bank > 0) {
            NourPrimaryButton(
                stringResource(Gendered(R.string.learn_use_minutes_m, R.string.learn_use_minutes_f).pick(gender)) + " (" + durationText(bank) + ")",
                controller::useMinutes,
            )
        }
        if (s.hasNext) {
            NourSecondaryButton(stringResource(R.string.learn_next_level), { controller.play(s.game, s.level + 1) })
        } else {
            NourSecondaryButton(stringResource(Gendered(R.string.learn_play_again_m, R.string.learn_play_again_f).pick(gender)), { controller.play(s.game, s.level) })
        }
        NourSecondaryButton(stringResource(R.string.learn_levels), { controller.back() })
    }
}
