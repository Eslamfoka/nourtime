package com.nourtime.app

import com.nourtime.app.feature.parent.OpenDeviceRequest
import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nourtime.app.core.designsystem.component.CenteredProgress
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.locale.AppLayoutDirection
import com.nourtime.app.core.locale.AppLocales
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.feature.home.MainRoute
import com.nourtime.app.feature.mode.ModeChooserScreen
import com.nourtime.app.feature.onboarding.OnboardingRoute
import com.nourtime.app.feature.parent.ParentRoute
import com.nourtime.app.feature.pin.UnlockRoute
import com.nourtime.app.feature.setup.timepicker.LocalTimePickerStyle
import com.nourtime.app.feature.setup.timepicker.TimePickerStyleViewModel
import com.nourtime.app.service.timer.TimerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboarding: OnboardingRepository

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequestedDevice(intent)
        // Protection runs once setup is complete; starting from the foreground is always allowed.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                onboarding.isComplete.filter { it }.collect { TimerService.start(this@MainActivity) }
            }
        }
        setContent {
            // The chosen language decides the direction (the activity's configuration carries it).
            AppLayoutDirection(LocalConfiguration.current.locales[0]) {
                NourTheme {
                    NourApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedDevice(intent)
    }

    /** A tapped push on the parent's phone names the child's phone to open. */
    private fun openRequestedDevice(intent: Intent?) {
        intent?.getStringExtra(OpenDeviceRequest.EXTRA_DEVICE_ID)?.let(OpenDeviceRequest::request)
    }
}

@Composable
private fun NourApp(viewModel: AppViewModel = hiltViewModel(), pickers: TimePickerStyleViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val pickerStyle by pickers.style.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    CompositionLocalProvider(LocalTimePickerStyle provides pickerStyle) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.95f)) togetherWith fadeOut(tween(180)) },
                label = "destination",
            ) { target ->
                when (target) {
                    // The app's background with a small spinner, only if deciding takes a moment.
                    AppDestination.LOADING -> CenteredProgress(Modifier.fillMaxSize())
                    AppDestination.MODE_CHOICE -> ModeChooserScreen(onChoose = viewModel::chooseMode)
                    AppDestination.PARENT -> ParentRoute(onBackToModeChoice = viewModel::backToModeChoice)
                    AppDestination.ONBOARDING -> OnboardingRoute(onBackToModeChoice = viewModel::backToModeChoice)
                    AppDestination.UNLOCK -> UnlockRoute()
                    AppDestination.HOME -> MainRoute()
                }
            }
        }
    }
}
