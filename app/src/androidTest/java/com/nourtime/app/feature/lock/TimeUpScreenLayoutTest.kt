package com.nourtime.app.feature.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nourtime.app.R
import com.nourtime.app.core.blocking.BlockDecision
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.remote.model.AskState
import com.nourtime.app.testing.Device
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Time's up" screen with everything on it (a call, Learn, Ask, an allowed app, OK) in a landscape
 * phone's height at a large font size: each of them can be scrolled fully into view, for every age.
 * The overlay can't fix the orientation, so this is a real case.
 */
@RunWith(AndroidJUnit4::class)
class TimeUpScreenLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    // The test activity only gets a window with the screen on and no lock screen.
    @Before
    fun setUp() = Device.wakeAndUnlock()

    private fun text(id: Int) = Device.context.getString(id)

    private fun state(age: AgeGroup) = LockScreenState(
        decision = BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = true),
        template = TemplateKind.DEFAULT,
        ageGroup = age,
        gender = ChildGender.BOY,
        countdownMs = 2 * 60 * 60_000L,
        countdownTotalMs = 6 * 60 * 60_000L,
        soundEnabled = false,
        allowedApps = listOf(AllowedApp("com.example.reader", "Reader", icon = null)),
        inCall = true,
    )

    @Test
    fun everyAction_reachable_inLandscapeAtLargeFont() {
        var age by mutableStateOf(AgeGroup.AGES_3_6)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                Box(Modifier.fillMaxWidth().height(LANDSCAPE_HEIGHT)) {
                    NourTheme(darkTheme = false) {
                        TimeUpScreen(
                            state = state(age),
                            onOk = {},
                            onParents = {},
                            ask = AskState.CanAsk,
                            onLearn = {},
                            onBackToCall = {},
                        )
                    }
                }
            }
        }
        AgeGroup.entries.forEach { group ->
            age = group
            compose.waitForIdle()
            // Each action can be scrolled fully into view (the scroll area isn't squeezed to a sliver).
            val scroll = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(text(R.string.tu_ok))))
            listOf(
                text(R.string.tu_back_to_call),
                text(R.string.learn_hub_button_m),
                text(R.string.tu_ask_m),
                "Reader",
                text(R.string.tu_ok),
            ).forEach { label ->
                scrollIntoView(scroll, label)
                val shown = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
                val area = scroll.fetchSemanticsNode().boundsInRoot
                assertTrue("$label in $group: $shown not inside $area", shown.height > 0 && shown.top >= area.top && shown.bottom <= area.bottom)
            }
        }
    }

    /**
     * Drags the screen just far enough to show [label] whole, as a child would. (`performScrollToNode`
     * doesn't move the outer scroll for the app inside the sideways row.)
     */
    private fun scrollIntoView(scroll: SemanticsNodeInteraction, label: String) {
        val node = compose.onNodeWithText(label).fetchSemanticsNode()
        val area = scroll.fetchSemanticsNode().boundsInRoot
        val top = node.positionInRoot.y
        val bottom = top + node.size.height
        val dy = when {
            bottom > area.bottom -> bottom - area.bottom
            top < area.top -> top - area.top
            else -> 0f
        }
        if (dy != 0f) scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        compose.waitForIdle()
    }

    private companion object {
        /** A phone on its side, without the status and navigation bars. */
        val LANDSCAPE_HEIGHT = 340.dp
    }
}
