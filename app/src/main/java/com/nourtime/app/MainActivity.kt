package com.nourtime.app

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.feature.home.MainRoute
import com.nourtime.app.feature.onboarding.OnboardingRoute
import com.nourtime.app.feature.pin.UnlockRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NourTheme {
                NourApp()
            }
        }
    }
}

@Composable
private fun NourApp(viewModel: AppViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.95f)) togetherWith fadeOut(tween(180)) },
            label = "destination",
        ) { target ->
            when (target) {
                AppDestination.LOADING -> Box(Modifier.fillMaxSize())
                AppDestination.ONBOARDING -> OnboardingRoute()
                AppDestination.UNLOCK -> UnlockRoute()
                AppDestination.HOME -> MainRoute()
            }
        }
    }
}
