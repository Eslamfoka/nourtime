package com.nourtime.app.feature.learning

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.Card
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.MathWriting
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.core.learning.Question
import com.nourtime.app.core.learning.Task
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered

private fun Task.instruction(): Int = when (this) {
    Task.SOLVE -> R.string.learn_task_solve
    Task.COMPARE -> R.string.learn_task_compare
    Task.LETTER_TO_PICTURE -> R.string.learn_task_letter_to_picture
    Task.LETTER_TO_WORD -> R.string.learn_task_letter_to_word
    Task.WORD_TO_PICTURE -> R.string.learn_task_word_to_picture
    Task.NAME_TO_COLOR -> R.string.learn_task_name_to_color
    Task.COLOR_TO_NAME -> R.string.learn_task_color_to_name
    Task.PICTURE_TO_WORD -> R.string.learn_task_picture_to_word
    Task.LISTEN_TO_PICTURE -> R.string.learn_task_listen_to_picture
    Task.LISTEN_TO_COLOR -> R.string.learn_task_listen_to_color
    Task.LISTEN_TO_LETTER -> R.string.learn_task_listen_to_letter
    Task.LISTEN_TO_NUMBER -> R.string.learn_task_listen_to_number
    Task.PATTERN -> R.string.learn_task_pattern
}

/** What a card says when tapped: numbers and words; pictures and colors stay quiet (they are answers). */
private fun Card.speech(): String? = when (this) {
    is Card.Number -> value.toString()
    is Card.Text -> speech.ifBlank { null }
    is Card.Sound -> speech
    else -> null
}

@Composable
internal fun ColumnScope.QuestionScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Playing, gender: ChildGender) {
    val round = s.round
    // The question just answered stays on screen while it's celebrated.
    val q = round.current ?: return
    val numerals = controller.numerals(state)
    val language = if (GameRegistry.of(s.game).wordsLanguage) controller.lettersLanguage(state) else controller.appLanguage
    val speaker = LocalSpeaker.current
    val voices by speaker.voices.collectAsStateWithLifecycle()
    val look = lookOf(s.game)

    // Letters questions are read aloud when they appear: the sound is part of the lesson.
    LaunchedEffect(round.index, s.level) {
        q.say?.let { speaker.say(it.text, it.language) }
    }

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    ProgressDots(round.questions.size, round.index, look.accent)
    Spacer(Modifier.height(12.dp))
    val message = when {
        s.celebrating != null -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender))
        round.isTutorialQuestion -> stringResource(Gendered(R.string.learn_tutorial_m, R.string.learn_tutorial_f).pick(gender))
        round.wrong.isNotEmpty() -> stringResource(Gendered(R.string.learn_try_again_m, R.string.learn_try_again_f).pick(gender))
        else -> stringResource(q.task.instruction())
    }
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (s.celebrating != null) NourPalette.MintDeep else NourPalette.Navy,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        PromptCard(q, numerals, language, hasVoice = language in voices, onSay = { text -> speaker.say(text, language) })
    }
    if (language !in voices && voices.isNotEmpty() && q.say != null) {
        Text(
            stringResource(R.string.learn_voice_missing),
            style = MaterialTheme.typography.bodySmall,
            color = NourPalette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Choices(
        q = q,
        numerals = numerals,
        wrong = round.wrong,
        celebrating = s.celebrating,
        tutorialTarget = if (round.isTutorialQuestion && s.celebrating == null) q.answer else null,
        onPick = { index ->
            q.choices[index].speech()?.let { speaker.say(it, language) }
            controller.pick(index)
        },
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ProgressDots(total: Int, done: Int, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == done) 14.dp else 10.dp)
                    .background(if (i < done) accent else if (i == done) NourPalette.Navy else NourPalette.Navy.copy(alpha = 0.2f), CircleShape),
            )
        }
    }
}

@Composable
private fun PromptCard(q: Question, numerals: NumeralStyle, language: LearnLanguage, hasVoice: Boolean, onSay: (String) -> Unit) {
    val sayAll = q.say?.text
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = NourPalette.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Math follows the numerals: right to left with ١٢٣ (Arabic schoolbooks), left to right with 123.
            // A number pattern is read like math too; shapes, colors and pictures follow the app.
            val numbers = q.task == Task.SOLVE || q.task == Task.COMPARE || (q.task == Task.PATTERN && q.prompt.any { it is Card.Number })
            val direction = if (numbers) mathDirection(numerals) else LocalLayoutDirection.current
            // A pattern shows up to seven small items in one line.
            val compact = q.task == Task.PATTERN
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                // Top-aligned: numbers with a column of dots below them keep the same line as the symbols.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = if (q.dots) Alignment.Top else Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val size = if (compact && q.prompt.none { it is Card.Number }) 40.sp else MathWriting.promptSize(q.prompt).sp
                    q.prompt.forEach { card ->
                        PromptItem(card, numerals, q.dots, big = q.prompt.size == 1, compact = compact, size = size, hasVoice = hasVoice, onSay = {
                            // A number says itself; a letter says "A, Apple" (the owner's design).
                            (if (card is Card.Number) card.speech() else sayAll ?: card.speech())?.let(onSay)
                        })
                    }
                }
            }
            // A speaker prompt is its own listen button.
            if (sayAll != null && hasVoice && q.prompt.none { it is Card.Sound }) {
                Spacer(Modifier.height(8.dp))
                Surface(onClick = { onSay(sayAll) }, shape = CircleShape, color = NourPalette.GoldLight, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = stringResource(R.string.learn_listen), tint = NourPalette.Navy)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptItem(card: Card, numerals: NumeralStyle, dots: Boolean, big: Boolean, compact: Boolean, size: TextUnit, hasVoice: Boolean, onSay: () -> Unit) {
    when (card) {
        is Card.Number -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(role = Role.Button, onClick = onSay)) {
            // In a pattern each number sits on its own chip, so "1 2 4 8 16" never reads as one number.
            val chip = if (compact) Modifier.background(NourPalette.GoldLight, RoundedCornerShape(12.dp)).padding(horizontal = 6.dp) else Modifier
            Text(numerals.format(card.value), fontSize = size, fontWeight = FontWeight.Bold, color = NourPalette.Navy, modifier = chip)
            if (dots) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 3,
                    modifier = Modifier.widthIn(max = 64.dp),
                ) {
                    repeat(card.value) { Box(Modifier.size(14.dp).background(NourPalette.Coral, CircleShape)) }
                }
            }
        }
        is Card.Symbol -> SymbolText(card, numerals, size, if (card.text == "?") NourPalette.GoldDeep else NourPalette.Navy)
        is Card.Text -> Text(
            card.text,
            fontSize = if (big) 96.sp else if (compact) 36.sp else 44.sp,
            fontWeight = FontWeight.Bold,
            color = NourPalette.Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(role = Role.Button, onClick = onSay).padding(horizontal = 8.dp),
        )
        is Card.Picture -> if (compact) LearningPicture(card.image, card.emoji, 40.dp, 30.sp) else LearningPicture(card.image, card.emoji, 112.dp, 72.sp)
        is Card.Sound -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onSay, shape = CircleShape, color = NourPalette.GoldLight, shadowElevation = 2.dp, modifier = Modifier.size(112.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.learn_listen),
                        tint = NourPalette.Navy,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            // No voice for this language on the phone: the child reads it instead.
            if (!hasVoice) {
                Text(card.number?.let(numerals::format) ?: card.text, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
            }
        }
        is Card.Swatch -> Box(
            Modifier
                .size(if (compact) 40.dp else 120.dp)
                .background(Color(card.argb), RoundedCornerShape(if (compact) 12.dp else 28.dp))
                .border(2.dp, NourPalette.Navy.copy(alpha = 0.15f), RoundedCornerShape(if (compact) 12.dp else 28.dp)),
        )
    }
}

@Composable
private fun Choices(
    q: Question,
    numerals: NumeralStyle,
    wrong: Set<Int>,
    celebrating: Int?,
    tutorialTarget: Int?,
    onPick: (Int) -> Unit,
) {
    val columns = if (q.choices.size == 3) 3 else 2
    val direction = if (q.task == Task.COMPARE) mathDirection(numerals) else LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            q.choices.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { i ->
                        ChoiceCard(
                            card = q.choices[i],
                            numerals = numerals,
                            wrong = i in wrong,
                            correct = celebrating == i,
                            pointed = tutorialTarget == i,
                            onClick = { onPick(i) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    card: Card,
    numerals: NumeralStyle,
    wrong: Boolean,
    correct: Boolean,
    pointed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong) shake.animateTo(0f, keyframes { durationMillis = 400; -14f at 50; 14f at 120; -10f at 190; 8f at 260; -4f at 330 })
    }
    val description = when (card) {
        is Card.Number -> numerals.format(card.value)
        is Card.Symbol -> MathWriting.glyph(card.text, MathWriting.rightToLeft(numerals))
        is Card.Text -> card.text
        is Card.Picture -> card.word
        is Card.Swatch -> card.name
        is Card.Sound -> card.text
    }
    Box(modifier) {
        Surface(
            onClick = onClick,
            enabled = !wrong,
            shape = RoundedCornerShape(24.dp),
            color = when {
                correct -> NourPalette.Mint
                wrong -> NourPalette.White.copy(alpha = 0.5f)
                else -> NourPalette.White
            },
            border = if (correct) BorderStroke(3.dp, NourPalette.MintDeep) else null,
            shadowElevation = if (wrong) 0.dp else 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .graphicsLayer { translationX = shake.value * density }
                .alpha(if (wrong) 0.45f else 1f)
                .semantics { contentDescription = description },
        ) {
            Box(Modifier.padding(8.dp).heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                when (card) {
                    is Card.Number -> Text(numerals.format(card.value), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
                    is Card.Symbol -> SymbolText(card, numerals, 40.sp, NourPalette.Navy)
                    // A single letter is shown big; words stay readable on two lines.
                    is Card.Text -> Text(card.text, fontSize = if (card.text.length <= 2) 44.sp else 24.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy, textAlign = TextAlign.Center, maxLines = 2)
                    is Card.Picture -> LearningPicture(card.image, card.emoji, 72.dp, 48.sp)
                    is Card.Swatch -> Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .background(Color(card.argb), RoundedCornerShape(16.dp))
                            .border(2.dp, NourPalette.Navy.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    )
                    is Card.Sound -> Unit
                }
                if (correct) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = NourPalette.White,
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(NourPalette.MintDeep, CircleShape).padding(4.dp),
                    )
                }
            }
        }
        if (pointed) TutorialHand(Modifier.align(Alignment.BottomCenter))
    }
}

/** The tutorial's pointing hand, bobbing over the right answer. */
@Composable
private fun TutorialHand(modifier: Modifier) {
    val bob by rememberInfiniteTransition(label = "hand").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "bob",
    )
    Text("👆", fontSize = 44.sp, modifier = modifier.offset(y = (28 + bob * 10).dp).graphicsLayer { alpha = 0.95f })
}

private fun mathDirection(numerals: NumeralStyle) =
    if (MathWriting.rightToLeft(numerals)) LayoutDirection.Rtl else LayoutDirection.Ltr

/**
 * A math sign, already mirrored by [MathWriting] where needed. Its text direction is pinned so the
 * system's bidi mirroring of < and > can't flip it a second time.
 */
@Composable
private fun SymbolText(card: Card.Symbol, numerals: NumeralStyle, size: TextUnit, color: Color) {
    Text(
        MathWriting.glyph(card.text, MathWriting.rightToLeft(numerals)),
        fontSize = size,
        fontWeight = FontWeight.Bold,
        color = color,
        style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
    )
}
