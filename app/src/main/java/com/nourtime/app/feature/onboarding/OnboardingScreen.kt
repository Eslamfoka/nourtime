package com.nourtime.app.feature.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.OemAutostart
import com.nourtime.app.core.ui.startFirstAvailable
import com.nourtime.app.data.onboarding.OnboardingStep

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val question by viewModel.question.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val current = step ?: return
    val canGoBack = viewModel.canGoBack(current)
    BackHandler(enabled = canGoBack) { viewModel.back() }
    val onBack = if (canGoBack) viewModel::back else null

    fun open(intents: List<android.content.Intent>) {
        if (!context.startFirstAvailable(intents)) {
            Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_LONG).show()
        }
    }

    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = { (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.97f)) togetherWith fadeOut(tween(160)) },
        label = "onboardingStep",
        modifier = Modifier.fillMaxSize(),
    ) { s ->
        val progress = viewModel.progressOf(s)
        Box(Modifier.fillMaxSize()) {
            when (s) {
                OnboardingStep.WELCOME -> WelcomeStep(onStart = viewModel::next)

                OnboardingStep.DISCLOSURE -> DisclosureStep(
                    progress = progress,
                    onBack = onBack,
                    onAccept = viewModel::next,
                )

                OnboardingStep.CREATE_PIN -> CreatePinStep(
                    progress = progress,
                    onBack = onBack,
                    state = pin,
                    onDigit = viewModel::onPinDigit,
                    onDelete = viewModel::onPinDelete,
                )

                OnboardingStep.SECURITY_QUESTION -> SecurityQuestionStep(
                    progress = progress,
                    onBack = onBack,
                    form = question,
                    onQuestionChange = viewModel::onQuestionChange,
                    onAnswerChange = viewModel::onAnswerChange,
                    onConfirmationChange = viewModel::onConfirmationChange,
                    onSave = viewModel::saveSecurityQuestion,
                )

                OnboardingStep.PERM_ACCESSIBILITY,
                OnboardingStep.PERM_USAGE_ACCESS,
                OnboardingStep.PERM_OVERLAY,
                OnboardingStep.PERM_DEVICE_ADMIN,
                OnboardingStep.PERM_NOTIFICATIONS,
                OnboardingStep.PERM_BATTERY,
                -> {
                    val permission = s.permission()
                    PermissionStep(
                        progress = progress,
                        onBack = onBack,
                        permission = permission,
                        granted = permissionStatus[permission] == true,
                        onGrant = {
                            if (permission == NourPermission.NOTIFICATIONS &&
                                viewModel.notificationsNeedRuntimeRequest && !askedForNotifications
                            ) {
                                askedForNotifications = true
                                notificationLauncher.launch(viewModel.notificationPermission)
                            } else {
                                open(viewModel.settingsIntents(permission))
                            }
                        },
                        onContinue = viewModel::next,
                        onSkip = if (permission == NourPermission.NOTIFICATIONS) viewModel::next else null,
                        onOpenAppInfo = if (permission == NourPermission.ACCESSIBILITY) {
                            { open(listOf(viewModel.appDetailsIntent())) }
                        } else {
                            null
                        },
                    )
                }

                OnboardingStep.AUTOSTART -> AutostartStep(
                    progress = progress,
                    onBack = onBack,
                    manufacturer = OemAutostart.manufacturerName,
                    onOpen = { open(OemAutostart.intents() + viewModel.appDetailsIntent()) },
                    onDone = viewModel::next,
                )

                OnboardingStep.CHILD_PROFILE -> ChildProfileStep(progress, onBack, onContinue = viewModel::next)

                OnboardingStep.SELECT_APPS -> SelectAppsStep(progress, onBack, onContinue = viewModel::next)

                OnboardingStep.TIME_BUDGET -> TimeBudgetStep(progress, onBack, onContinue = viewModel::next)

                OnboardingStep.FINISHED -> FinishedStep(onFinish = viewModel::finish)
            }
        }
    }
}

private fun OnboardingStep.permission(): NourPermission = when (this) {
    OnboardingStep.PERM_ACCESSIBILITY -> NourPermission.ACCESSIBILITY
    OnboardingStep.PERM_USAGE_ACCESS -> NourPermission.USAGE_ACCESS
    OnboardingStep.PERM_OVERLAY -> NourPermission.OVERLAY
    OnboardingStep.PERM_DEVICE_ADMIN -> NourPermission.DEVICE_ADMIN
    OnboardingStep.PERM_NOTIFICATIONS -> NourPermission.NOTIFICATIONS
    OnboardingStep.PERM_BATTERY -> NourPermission.BATTERY
    else -> error("$this is not a permission step")
}
