package com.nourtime.app.feature.learning

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.LetterTile
import com.nourtime.app.core.learning.WordRound
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Word Builder: see a picture, spell its word with letter tiles (tap them, or drag them up). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.WordsScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Words, gender: ChildGender) {
    val numerals = controller.numerals(state)
    val look = lookOf(s.game)
    val round = s.round
    val speaker = LocalSpeaker.current
    val language = controller.lettersLanguage(state)
    // While a finished word is celebrated, the round has already moved on: show the finished one.
    val shown = if (s.celebrating != null) round.tasks[round.index - 1] else round.current ?: return
    val say = { text: String -> speaker.say(text, language) }

    LaunchedEffect(round.index, s.celebrating) {
        if (s.celebrating != null) say(s.celebrating) else say(shown.word.word)
    }

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.celebrating != null -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender))
        s.lastWrong -> stringResource(Gendered(R.string.learn_words_wrong_m, R.string.learn_words_wrong_f).pick(gender))
        round.tutorial && round.index == 0 && round.placed.isEmpty() -> stringResource(Gendered(R.string.learn_tutorial_words_m, R.string.learn_tutorial_words_f).pick(gender))
        else -> stringResource(Gendered(R.string.learn_task_words_m, R.string.learn_task_words_f).pick(gender))
    }
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (s.celebrating != null) NourPalette.MintDeep else NourPalette.Navy,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // The picture; tapping it says the word again.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = NourPalette.White,
            shadowElevation = 3.dp,
            modifier = Modifier.clickable(role = Role.Button) { say(shown.word.word) },
        ) {
            Box(Modifier.padding(16.dp)) { LearningPicture(shown.word.image, shown.word.emoji, 120.dp, 84.sp, description = shown.word.word) }
        }
        if (round.hint && s.celebrating == null) {
            Text(shown.word.word, fontSize = 30.sp, color = NourPalette.Navy.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
        }
        // The word so far, joined as it's written, then one circle per letter still to come.
        val built = s.celebrating ?: round.built
        Text(
            built.ifEmpty { " " },
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = if (s.celebrating != null) NourPalette.MintDeep else NourPalette.Navy,
            modifier = Modifier.semantics { contentDescription = built },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = shown.letters.size
            val filled = if (s.celebrating != null) total else round.placed.size
            repeat(total) { i ->
                Box(Modifier.size(14.dp).background(if (i < filled) look.accent else NourPalette.Navy.copy(alpha = 0.15f), CircleShape))
            }
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        val tiles = if (s.celebrating != null) emptyList() else shown.tiles
        tiles.forEach { tile ->
            if (round.isPlaced(tile)) {
                // Keep the place empty so the other tiles don't jump around.
                Spacer(Modifier.size(64.dp))
            } else {
                LetterTileView(
                    tile = tile,
                    hinted = round.tutorial && round.index == 0 && round.placed.isEmpty() && tile.letter == round.next,
                    accent = look.accent,
                    onPlace = {
                        val outcome = controller.wordPlace(tile)
                        if (outcome == WordRound.Outcome.PLACED) say(tile.letter)
                        outcome
                    },
                )
            }
        }
    }
}

@Composable
private fun LetterTileView(tile: LetterTile, hinted: Boolean, accent: androidx.compose.ui.graphics.Color, onPlace: () -> WordRound.Outcome) {
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val upEnough = with(LocalDensity.current) { 72.dp.toPx() }
    fun place() {
        if (onPlace() == WordRound.Outcome.WRONG) {
            scope.launch { shake.animateTo(0f, keyframes { durationMillis = 400; -14f at 50; 14f at 120; -10f at 190; 8f at 260; -4f at 330 }) }
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (hinted) accent else NourPalette.White,
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(64.dp)
            .offset { IntOffset((drag.x + shake.value * density).roundToInt(), drag.y.roundToInt()) }
            .semantics {
                contentDescription = tile.letter
                role = Role.Button
            }
            .pointerInput(tile) { detectTapGestures { place() } }
            .pointerInput(tile) {
                // Dragging a tile up onto the word puts it there; anywhere else it springs back.
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                    onDragEnd = {
                        val up = -drag.y > upEnough
                        drag = Offset.Zero
                        if (up) place()
                    },
                    onDragCancel = { drag = Offset.Zero },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(tile.letter, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
        }
    }
}
