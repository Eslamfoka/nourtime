package com.nourtime.app.feature.onboarding

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.MainActivity
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.OemAutostart
import com.nourtime.app.core.permissions.PermissionChecker
import com.nourtime.app.core.security.ParentSession
import com.nourtime.app.core.security.PinCreation
import com.nourtime.app.core.security.PinCreationState
import com.nourtime.app.core.security.SecurityQuestionValidator
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.data.onboarding.OnboardingStep
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.security.SecurityRepository
import com.nourtime.app.data.settings.ParentSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SecurityQuestionForm(
    val question: String = "",
    val answer: String = "",
    val confirmation: String = "",
    val errors: Set<SecurityQuestionValidator.Error> = emptySet(),
    val saving: Boolean = false,
)

/** A limited app the parent can open on the "Test protection" step. */
data class TestApp(val packageName: String, val label: String)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboarding: OnboardingRepository,
    private val security: SecurityRepository,
    private val permissions: PermissionChecker,
    private val session: ParentSession,
    @ApplicationContext private val context: Context,
    private val tracker: ForegroundAppTracker,
    private val settings: ParentSettingsRepository,
    private val apps: InstalledAppsRepository,
) : ViewModel() {

    /** Steps for this device; the autostart step only appears on OEMs that need it. */
    val steps: List<OnboardingStep> =
        OnboardingStep.entries.filter { it != OnboardingStep.AUTOSTART || OemAutostart.isRelevant() }

    val step: StateFlow<OnboardingStep?> = onboarding.step.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissionStatus = MutableStateFlow(permissions.statusOfAll())
    val permissionStatus: StateFlow<Map<NourPermission, Boolean>> = _permissionStatus.asStateFlow()

    private val _pin = MutableStateFlow(PinCreationState())
    val pin: StateFlow<PinCreationState> = _pin.asStateFlow()

    private val _question = MutableStateFlow(SecurityQuestionForm())
    val question: StateFlow<SecurityQuestionForm> = _question.asStateFlow()

    /** 1-based position among the numbered steps (everything between Welcome and Finished). */
    fun progressOf(step: OnboardingStep): Pair<Int, Int>? {
        if (step == OnboardingStep.WELCOME || step == OnboardingStep.FINISHED) return null
        return steps.indexOf(step) to steps.size - 2
    }

    fun next() {
        val current = step.value ?: return
        viewModelScope.launch { advanceFrom(current) }
    }

    fun canGoBack(step: OnboardingStep): Boolean {
        if (step == OnboardingStep.CREATE_PIN && _pin.value.stage == PinCreationState.Stage.CONFIRM) return true
        val previous = steps.getOrNull(steps.indexOf(step) - 1) ?: return false
        // Saved security steps are never revisited (their forms would come back empty);
        // the PIN and question can be changed later from settings.
        return step != OnboardingStep.FINISHED &&
            previous != OnboardingStep.CREATE_PIN &&
            previous != OnboardingStep.SECURITY_QUESTION
    }

    fun back() {
        val current = step.value ?: return
        if (!canGoBack(current)) return
        if (current == OnboardingStep.CREATE_PIN && _pin.value.stage == PinCreationState.Stage.CONFIRM) {
            _pin.value = PinCreationState()
            return
        }
        viewModelScope.launch { onboarding.setStep(steps[steps.indexOf(current) - 1]) }
    }

    fun refreshPermissions() {
        _permissionStatus.value = permissions.statusOfAll()
    }

    fun settingsIntents(permission: NourPermission): List<Intent> = permissions.settingsIntents(permission)

    private var watch: Job? = null

    /**
     * After the parent opens a Settings screen, brings onboarding back as soon as the permission is
     * on, so they don't have to find their way back. Android lets Nour Time do this once its
     * Accessibility service is bound, which is the first permission step.
     */
    fun watchUntilGranted(permission: NourPermission) {
        watch?.cancel()
        watch = viewModelScope.launch {
            withTimeoutOrNull(WATCH_MS) {
                while (!permissions.isGranted(permission)) delay(WATCH_POLL_MS)
                refreshPermissions()
                bringToFront()
            }
        }
    }

    private fun bringToFront() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
    }

    // --- "Test protection" step ---

    private val _testApp = MutableStateFlow<TestApp?>(null)
    val testApp: StateFlow<TestApp?> = _testApp.asStateFlow()

    /** Label of the limited app Nour Time saw on screen during the test, once it has. */
    private val _testDetected = MutableStateFlow<String?>(null)
    val testDetected: StateFlow<String?> = _testDetected.asStateFlow()

    init {
        viewModelScope.launch {
            step.collectLatest { current ->
                if (current != OnboardingStep.TEST_PROTECTION) return@collectLatest
                val limited = settings.settings.first().limitedApps
                _testApp.value = limited
                    .filter { context.packageManager.getLaunchIntentForPackage(it) != null }
                    .map { TestApp(it, apps.label(it)) }
                    .minByOrNull { it.label }
                if (_testDetected.value != null) return@collectLatest
                combine(tracker.state, settings.settings) { fg, s -> fg.limitedInUse(s.limitedApps).firstOrNull() }
                    .collect { pkg ->
                        if (pkg != null && _testDetected.value == null) {
                            _testDetected.value = apps.label(pkg)
                            bringToFront()
                        }
                    }
            }
        }
    }

    fun openTestApp() {
        val app = _testApp.value ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun appDetailsIntent(): Intent = permissions.appDetailsIntent()

    val notificationsNeedRuntimeRequest: Boolean get() = permissions.notificationsNeedRuntimeRequest

    val notificationPermission: String get() = permissions.notificationPermission

    fun onPinDigit(digit: Char) {
        val updated = PinCreation.onDigit(_pin.value, digit)
        _pin.value = updated
        val pin = updated.completedPin ?: return
        viewModelScope.launch {
            // Unlock first so the app doesn't ask for the PIN the parent just created.
            session.unlock()
            security.setPin(pin)
            advanceFrom(OnboardingStep.CREATE_PIN)
            _pin.value = PinCreationState()
        }
    }

    fun onPinDelete() {
        _pin.value = PinCreation.onDelete(_pin.value)
    }

    fun onQuestionChange(value: String) = _question.update { it.copy(question = value, errors = emptySet()) }

    fun onAnswerChange(value: String) = _question.update { it.copy(answer = value, errors = emptySet()) }

    fun onConfirmationChange(value: String) = _question.update { it.copy(confirmation = value, errors = emptySet()) }

    fun saveSecurityQuestion() {
        val form = _question.value
        if (form.saving) return
        val errors = SecurityQuestionValidator.validate(form.question, form.answer, form.confirmation)
        if (errors.isNotEmpty()) {
            _question.update { it.copy(errors = errors) }
            return
        }
        _question.update { it.copy(saving = true) }
        viewModelScope.launch {
            security.setSecurityQuestion(form.question, form.answer)
            advanceFrom(OnboardingStep.SECURITY_QUESTION)
            _question.value = SecurityQuestionForm()
        }
    }

    fun finish() {
        viewModelScope.launch { onboarding.markComplete() }
    }

    private suspend fun advanceFrom(current: OnboardingStep) {
        val next = steps.getOrNull(steps.indexOf(current) + 1) ?: return
        onboarding.setStep(next)
    }

    private companion object {
        const val WATCH_MS = 3 * 60_000L
        const val WATCH_POLL_MS = 500L
    }
}
