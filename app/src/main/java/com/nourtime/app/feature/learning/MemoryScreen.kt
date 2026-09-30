package com.nourtime.app.feature.learning

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.Card
import com.nourtime.app.core.learning.MemoryGame
import com.nourtime.app.core.learning.MemoryRound
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered
import kotlin.math.ceil
import kotlin.math.sqrt

/** [count] dots in a neat grid, a short last row centered. */
@Composable
internal fun DotsFace(count: Int, size: Dp) {
    Canvas(Modifier.size(size)) {
        val cols = ceil(sqrt(count.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(count / cols.toFloat()).toInt().coerceAtLeast(1)
        val cell = minOf(this.size.width / cols, this.size.height / rows)
        val r = cell * 0.32f
        val ox = (this.size.width - cols * cell) / 2
        val oy = (this.size.height - rows * cell) / 2
        repeat(count) { i ->
            val row = i / cols
            val inRow = if (row == rows - 1) count - row * cols else cols
            val shift = (cols - inRow) * cell / 2
            drawCircle(NourPalette.Coral, r, Offset(ox + shift + (i % cols + 0.5f) * cell, oy + (row + 0.5f) * cell))
        }
    }
}

/** Memory Match: a table of face-down cards; turn two at a time to find the pairs. */
@Composable
internal fun ColumnScope.MemoryScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Memory, gender: ChildGender) {
    val numerals = controller.numerals(state)
    val look = lookOf(s.game)
    val round = s.round
    val speaker = LocalSpeaker.current
    val language = controller.lettersLanguage(state)

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.celebrating -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender))
        round.tutorial && round.moves == 0 -> stringResource(Gendered(R.string.learn_tutorial_memory_m, R.string.learn_tutorial_memory_f).pick(gender))
        else -> stringResource(Gendered(R.string.learn_task_memory_m, R.string.learn_task_memory_f).pick(gender))
    }
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (s.celebrating) NourPalette.MintDeep else NourPalette.Navy,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Text(
        stringResource(R.string.learn_memory_moves, numerals.format(round.moves)),
        style = MaterialTheme.typography.bodyMedium,
        color = NourPalette.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    val cols = MemoryGame.columns(round.cards.size)
    val rows = (round.cards.size + cols - 1) / cols
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 10.dp
        // Cards as big as fit, a little taller than wide.
        val cardW = min((maxWidth - gap * (cols - 1)) / cols, ((maxHeight - gap * (rows - 1)) / rows) / 1.2f)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            round.cards.indices.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { i ->
                        val face = round.cards[i].face
                        MemoryCardView(
                            face = face,
                            up = round.isUp(i),
                            matched = i in round.matched,
                            accent = look.accent,
                            numerals = numerals,
                            width = cardW,
                            onClick = {
                                if (controller.memoryTurn(i) != MemoryRound.Outcome.IGNORED) {
                                    // A turned card says what it shows (words, letters, numbers, pictures).
                                    when (face) {
                                        is Card.Text -> face.speech.ifBlank { null }
                                        is Card.Number -> face.value.toString()
                                        is Card.Dots -> face.count.toString()
                                        is Card.Picture -> face.word
                                        is Card.Swatch -> face.name
                                        else -> null
                                    }?.let { speaker.say(it, language) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun MemoryCardView(face: Card, up: Boolean, matched: Boolean, accent: Color, numerals: NumeralStyle, width: Dp, onClick: () -> Unit) {
    // A flip: the card turns edge-on, then shows the other side.
    val angle by animateFloatAsState(if (up) 180f else 0f, tween(300), label = "flip")
    val showFace = angle > 90f
    val description = if (up) describe(face, numerals) else stringResource(R.string.learn_memory_card_down)
    Surface(
        onClick = onClick,
        enabled = !up,
        shape = RoundedCornerShape(16.dp),
        color = if (showFace) NourPalette.White else accent,
        shadowElevation = if (matched) 0.dp else 3.dp,
        modifier = Modifier
            .size(width, width * 1.2f)
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 12f * density
            }
            .semantics { contentDescription = description }
            .then(if (matched) Modifier.border(3.dp, NourPalette.MintDeep, RoundedCornerShape(16.dp)) else Modifier),
    ) {
        // The face is drawn mirrored back, so it reads the right way after the flip.
        Box(Modifier.fillMaxSize().graphicsLayer { rotationY = if (showFace) 180f else 0f }.padding(6.dp), contentAlignment = Alignment.Center) {
            if (!showFace) {
                Text("⭐", fontSize = (width.value * 0.35f).sp)
            } else {
                val big = (width.value * 0.42f).sp
                when (face) {
                    is Card.Picture -> LearningPicture(face.image, face.emoji, width * 0.7f, (width.value * 0.45f).sp)
                    is Card.Text -> Text(
                        face.text,
                        fontSize = if (face.text.length <= 2) big else (width.value * 0.18f).sp,
                        fontWeight = FontWeight.Bold,
                        color = NourPalette.Navy,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    is Card.Number -> Text(numerals.format(face.value), fontSize = big, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
                    is Card.Dots -> DotsFace(face.count, width * 0.8f)
                    is Card.Swatch -> Box(Modifier.fillMaxSize().background(Color(face.argb), RoundedCornerShape(10.dp)))
                    else -> Unit
                }
            }
        }
    }
}

private fun describe(face: Card, numerals: NumeralStyle): String = when (face) {
    is Card.Picture -> face.word
    is Card.Text -> face.text
    is Card.Number -> numerals.format(face.value)
    is Card.Dots -> numerals.format(face.count)
    is Card.Swatch -> face.name
    else -> ""
}
