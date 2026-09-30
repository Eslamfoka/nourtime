package com.nourtime.app.feature.learning

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.Card
import com.nourtime.app.core.learning.SortRound
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Sorting: one thing at a time in the middle; drag it into its group below (or tap the group). */
@Composable
internal fun ColumnScope.SortingScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Sorting, gender: ChildGender) {
    val numerals = controller.numerals(state)
    val look = lookOf(s.game)
    val round = s.round
    val speaker = LocalSpeaker.current
    val language = controller.lettersLanguage(state)
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val binBounds = remember { mutableStateMapOf<Int, Rect>() }
    var itemBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }

    fun drop(bin: Int) {
        when (controller.sortDrop(bin)) {
            SortRound.Outcome.WRONG -> scope.launch { shake.animateTo(0f, keyframes { durationMillis = 400; -14f at 50; 14f at 120; -10f at 190; 8f at 260; -4f at 330 }) }
            SortRound.Outcome.RIGHT, SortRound.Outcome.DONE -> speaker.say(round.bins[bin].label, language)
            SortRound.Outcome.IGNORED -> Unit
        }
    }

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.celebrating -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender))
        s.lastWrong -> stringResource(Gendered(R.string.learn_sorting_wrong_m, R.string.learn_sorting_wrong_f).pick(gender))
        else -> stringResource(Gendered(R.string.learn_task_sorting_m, R.string.learn_task_sorting_f).pick(gender))
    }
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (s.celebrating) NourPalette.MintDeep else NourPalette.Navy,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    // Progress: one dot per thing to sort.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        repeat(round.items.size) { i ->
            Box(Modifier.size(10.dp).background(if (i < round.index) look.accent else NourPalette.Navy.copy(alpha = 0.15f), CircleShape))
        }
    }
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val item = round.current
        if (item != null && !s.celebrating) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = NourPalette.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(150.dp)
                    // Absolute: the card follows the finger; a plain offset is mirrored in right-to-left layouts.
                    .absoluteOffset { IntOffset((drag.x + shake.value * density).roundToInt(), drag.y.roundToInt()) }
                    .onGloballyPositioned { itemBounds = it.boundsInRoot().translate(-drag) }
                    .semantics { contentDescription = describeSortItem(item.card, numerals) }
                    .pointerInput(round.index) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                drag += amount
                            },
                            onDragEnd = {
                                // Dropped over a group: that's the answer; anywhere else it springs back.
                                val center = itemBounds.center + drag
                                drag = Offset.Zero
                                binBounds.entries.firstOrNull { it.value.contains(center) }?.let { drop(it.key) }
                            },
                            onDragCancel = { drag = Offset.Zero },
                        )
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (val c = item.card) {
                        is Card.Picture -> LearningPicture(c.image, c.emoji, 110.dp, 80.sp)
                        is Card.Number -> Text(numerals.format(c.value), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
                        else -> Unit
                    }
                }
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        round.bins.forEachIndexed { i, bin ->
            Surface(
                onClick = { drop(i) },
                shape = RoundedCornerShape(24.dp),
                color = look.accent.copy(alpha = 0.18f),
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .border(3.dp, look.accent, RoundedCornerShape(24.dp))
                    .onGloballyPositioned { binBounds[i] = it.boundsInRoot() }
                    .semantics { contentDescription = bin.label },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp),
                ) {
                    if (bin.emoji.isNotEmpty()) Text(bin.emoji, fontSize = 40.sp)
                    Text(bin.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy, textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    // How many are in the group so far.
                    Text(numerals.format(round.count(i)), fontSize = 16.sp, color = NourPalette.Muted)
                }
            }
        }
    }
}

private fun describeSortItem(card: Card, numerals: com.nourtime.app.core.learning.NumeralStyle): String = when (card) {
    is Card.Picture -> card.word
    is Card.Number -> numerals.format(card.value)
    else -> ""
}
