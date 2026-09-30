package com.nourtime.app.feature.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.ui.formatCountdown
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.feature.setup.durationText
import com.nourtime.app.remote.model.AskState

/**
 * The child's "Time's up" screen (brief §4, §12): playful and positive, never a punishment.
 * [onOk] is null when the whole device is locked (only the parent can lift it). [ask] is null when
 * there's no parent's phone to ask (not paired, or not a time-up lock).
 */
@Composable
fun TimeUpScreen(
    state: LockScreenState,
    onOk: (() -> Unit)?,
    onParents: () -> Unit,
    onOpenApp: (String) -> Unit = {},
    ask: AskState? = null,
    onAsk: () -> Unit = {},
    onLearn: (() -> Unit)? = null,
) {
    val style = styleFor(state.template).forAge(state.ageGroup)
    val text = if (style.dark) NourPalette.Cream else NourPalette.Navy
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(style.background)),
    ) {
        Decorations(state.template)
        AnimatedContent(
            targetState = shown,
            transitionSpec = { (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.9f)) togetherWith fadeOut() },
            label = "timeUp",
        ) { visible ->
            if (!visible) return@AnimatedContent
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ParentsButton(text, onParents, Modifier.align(Alignment.End))
                CenteredScrollColumn(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.ageGroup) {
                        AgeGroup.AGES_3_6 -> YoungLayout(state, style, text)
                        AgeGroup.AGES_7_9 -> MiddleLayout(state, style, text)
                        AgeGroup.AGES_10_12 -> OlderLayout(state, style, text)
                    }
                }
                if (onLearn != null) LearnButton(state, onLearn)
                if (ask != null) AskRow(ask, state, text, onAsk)
                if (state.allowedApps.isNotEmpty()) AllowedAppsRow(state, text, onOpenApp)
                if (onOk != null) OkButton(onOk)
            }
        }
    }
}

/** Short phones (roughly under 700 dp tall) get a smaller Nour so everything fits. */
@Composable
private fun compactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < 700

/** Ages 3–6: a big Nour in the middle and almost no text. */
@Composable
private fun YoungLayout(state: LockScreenState, style: TemplateStyle, text: Color) {
    NourStar(Modifier.size(if (compactHeight()) 190.dp else 280.dp), pose = style.pose)
    Text(
        stringResource(style.title.pick(state.gender)),
        style = MaterialTheme.typography.headlineLarge,
        color = text,
        textAlign = TextAlign.Center,
    )
}

/** Ages 7–9: short text, a round countdown and three off-screen ideas. */
@Composable
private fun MiddleLayout(state: LockScreenState, style: TemplateStyle, text: Color) {
    NourStar(Modifier.size(if (compactHeight()) 120.dp else 180.dp), pose = style.pose)
    Text(
        stringResource(style.title.pick(state.gender)),
        style = MaterialTheme.typography.headlineMedium,
        color = text,
        textAlign = TextAlign.Center,
    )
    state.countdownMs?.let { ms ->
        Spacer(Modifier.height(16.dp))
        CountdownRing(ms, text)
    }
    if (style.activities.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            style.activities.forEach { activity ->
                Surface(shape = MaterialTheme.shapes.medium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(activity.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = NourPalette.Navy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** Ages 10–12: calmer, a clear countdown and a motivational line, no big character. */
@Composable
private fun OlderLayout(state: LockScreenState, style: TemplateStyle, text: Color) {
    NourStar(Modifier.size(72.dp), pose = style.pose, animated = false)
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(style.title.pick(state.gender)),
        style = MaterialTheme.typography.headlineMedium,
        color = text,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(Gendered(R.string.tu_bravo_m, R.string.tu_bravo_f).pick(state.gender)),
        style = MaterialTheme.typography.bodyLarge,
        color = text.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
    )
    state.countdownMs?.let { ms ->
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.tu_back_in_label), style = MaterialTheme.typography.labelLarge, color = text.copy(alpha = 0.8f))
        Text(formatCountdown(ms), style = MaterialTheme.typography.displayMedium, color = text)
    }
}

@Composable
private fun CountdownRing(ms: Long, text: Color) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 10.dp.toPx()
            drawArc(text.copy(alpha = 0.15f), 0f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            drawArc(NourPalette.Gold, -90f, 300f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.tu_back_in_label), style = MaterialTheme.typography.labelSmall, color = text.copy(alpha = 0.8f))
            Text(formatCountdown(ms), style = MaterialTheme.typography.titleLarge, color = text)
        }
    }
}

/** Small and subtle so it doesn't catch the child's attention (brief §12). */
@Composable
private fun ParentsButton(text: Color, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, modifier = modifier.alpha(0.55f).heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.tu_parents_button), style = MaterialTheme.typography.labelMedium, color = text)
    }
}

/** "You can still open": the educational apps allowed during the lock, big enough for small hands. */
@Composable
private fun AllowedAppsRow(state: LockScreenState, text: Color, onOpenApp: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Gendered(R.string.tu_allowed_title_m, R.string.tu_allowed_title_f).pick(state.gender)),
            style = MaterialTheme.typography.titleMedium,
            color = text,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.allowedApps.forEach { app ->
                Surface(
                    onClick = { onOpenApp(app.packageName) },
                    shape = MaterialTheme.shapes.large,
                    color = NourPalette.Cream,
                    contentColor = NourPalette.Navy,
                    shadowElevation = 4.dp,
                    modifier = Modifier.widthIn(min = 96.dp, max = 120.dp),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(icon, contentDescription = null, modifier = Modifier.size(56.dp))
                        } else {
                            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(56.dp))
                        }
                        Text(app.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** Ask the parent's phone for more time (Phase 4c); the answer arrives as a bonus. */
@Composable
private fun AskRow(ask: AskState, state: LockScreenState, text: Color, onAsk: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
        when (ask) {
            AskState.CanAsk -> Surface(
                onClick = onAsk,
                shape = CircleShape,
                color = NourPalette.Cream,
                contentColor = NourPalette.Navy,
                shadowElevation = 4.dp,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.MoreTime, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Gendered(R.string.tu_ask_m, R.string.tu_ask_f).pick(state.gender)), style = MaterialTheme.typography.titleMedium)
                }
            }
            AskState.Waiting -> AskMessage(stringResource(Gendered(R.string.tu_ask_waiting_m, R.string.tu_ask_waiting_f).pick(state.gender)), text)
            is AskState.Approved -> AskMessage(stringResource(R.string.tu_ask_approved, durationText(ask.minutes)), text)
            is AskState.Declined -> AskMessage(stringResource(Gendered(R.string.tu_ask_declined_m, R.string.tu_ask_declined_f).pick(state.gender)), text)
        }
    }
}

/** Opens the Learning Hub: the most inviting thing on the screen. */
@Composable
private fun LearnButton(state: LockScreenState, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        // Coral: stands apart from the mint OK button, readable on light and dark (sleep) themes.
        color = NourPalette.Coral,
        contentColor = NourPalette.Navy,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(bottom = 12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("🧩", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(Gendered(R.string.learn_hub_button_m, R.string.learn_hub_button_f).pick(state.gender)),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun AskMessage(message: String, text: Color) {
    Text(message, style = MaterialTheme.typography.titleMedium, color = text, textAlign = TextAlign.Center)
}

@Composable
private fun OkButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = NourTheme.colors.success,
        contentColor = NourPalette.Navy,
        modifier = Modifier.widthIn(min = 160.dp).heightIn(min = 64.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.tu_ok), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Clouds for daytime themes; twinkling stars and a moon at night. */
@Composable
private fun Decorations(kind: TemplateKind) {
    Canvas(Modifier.fillMaxSize()) {
        when (kind) {
            TemplateKind.SLEEP -> {
                val stars = listOf(0.1f to 0.12f, 0.3f to 0.06f, 0.55f to 0.15f, 0.8f to 0.08f, 0.9f to 0.3f, 0.15f to 0.35f, 0.7f to 0.4f)
                stars.forEachIndexed { i, (x, y) ->
                    drawCircle(NourPalette.Cream.copy(alpha = 0.5f + 0.1f * (i % 3)), 3.dp.toPx() + (i % 2), Offset(size.width * x, size.height * y))
                }
                val moon = Offset(size.width * 0.82f, size.height * 0.16f)
                drawCircle(NourPalette.GoldLight, 34.dp.toPx(), moon)
                drawCircle(Color(0xFF17203A), 30.dp.toPx(), Offset(moon.x - 16.dp.toPx(), moon.y - 8.dp.toPx()))
            }
            TemplateKind.PLAY, TemplateKind.DEFAULT, TemplateKind.STUDY -> {
                val cloud = Color.White.copy(alpha = 0.7f)
                listOf(0.18f to 0.16f, 0.78f to 0.1f, 0.62f to 0.82f).forEach { (x, y) ->
                    val c = Offset(size.width * x, size.height * y)
                    val r = 28.dp.toPx()
                    drawCircle(cloud, r, c)
                    drawCircle(cloud, r * 0.8f, Offset(c.x - r, c.y + r * 0.2f))
                    drawCircle(cloud, r * 0.8f, Offset(c.x + r, c.y + r * 0.2f))
                }
            }
            else -> Unit
        }
    }
}

/** Parent side of the lock screen: PIN, then (during a lock period) the security question. */
@Composable
fun ParentPanelFrame(onBack: () -> Unit, content: @Composable () -> Unit) {
    CenteredScrollColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        content()
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
