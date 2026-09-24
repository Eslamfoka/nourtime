package com.nourtime.app.service.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nourtime.app.R
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.SecurityRepository
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.feature.lock.LockOverlayContent
import com.nourtime.app.feature.lock.LockScreenState
import com.nourtime.app.feature.lock.OverlayParentFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hosts the full-screen "Time's up" window. With Accessibility on it is an accessibility overlay,
 * which sits above every app including picture-in-picture; otherwise (fail-closed fallback) it is a
 * regular "display over other apps" window.
 */
@Singleton
class LockOverlay @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val security: SecurityRepository,
    private val pass: ParentPass,
    private val clock: DeviceClock,
) {
    private var accessibility: AccessibilityService? = null
    private var window: OverlayWindow? = null
    private val state = MutableStateFlow<LockScreenState?>(null)

    /** Called when the child taps "OK" on a dismissable screen. */
    var onChildDismiss: () -> Unit = {}

    @MainThread
    fun attach(service: AccessibilityService) {
        accessibility = service
        reopen()
    }

    @MainThread
    fun detach(service: AccessibilityService) {
        if (accessibility === service) {
            accessibility = null
            reopen()
        }
    }

    @MainThread
    fun render(next: LockScreenState?) {
        val wasShown = window != null
        state.value = next
        when {
            next != null && !wasShown -> open(next)
            next == null && wasShown -> close()
        }
    }

    /** Sends the child to the home screen, away from the limited app. */
    @MainThread
    fun goHome() {
        val done = accessibility?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true
        if (!done) {
            runCatching {
                appContext.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun reopen() {
        val current = state.value ?: return
        close()
        open(current)
    }

    private fun open(first: LockScreenState) {
        val host = accessibility
        val context: Context = host ?: appContext
        val type = if (host != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val w = OverlayWindow(context, type)
        Log.i(TAG, "open overlay, accessibility=${host != null}")
        if (!w.show()) return
        window = w
        takeAudioFocus()
        if (first.soundEnabled && first.ageGroup != AgeGroup.AGES_10_12) playChime()
    }

    private fun close() {
        window?.dismiss()
        window = null
        releaseAudioFocus()
    }

    // --- audio: pause whatever the limited app was playing, then a soft chime ---

    private val audioManager by lazy { appContext.getSystemService(AudioManager::class.java) }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .build()
    }

    private fun takeAudioFocus() {
        runCatching { audioManager.requestAudioFocus(focusRequest) }
    }

    private fun releaseAudioFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    private fun playChime() {
        runCatching {
            MediaPlayer.create(
                appContext,
                R.raw.nour_chime,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
                audioManager.generateAudioSessionId(),
            )?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }

    /** One overlay window with its own lifecycle, so Compose can run outside an Activity. */
    private inner class OverlayWindow(private val context: Context, private val type: Int) :
        SavedStateRegistryOwner, ViewModelStoreOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val parentFlow = OverlayParentFlow(scope, security, clock, pass)
        private val wm = context.getSystemService(WindowManager::class.java)

        private val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) parentFlow.back()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        fun show(): Boolean {
            savedState.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            val compose = ComposeView(context).apply {
                setContent {
                    val s by state.collectAsStateWithLifecycle()
                    val stage by parentFlow.stage.collectAsStateWithLifecycle()
                    NourTheme(darkTheme = false) {
                        AnimatedContent(s != null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "overlay") { visible ->
                            val current = s
                            if (visible && current != null) {
                                LockOverlayContent(
                                    state = current,
                                    stage = stage,
                                    parentFlow = parentFlow,
                                    onChildOk = { onChildDismiss() },
                                )
                            }
                        }
                    }
                }
            }
            root.addView(compose)
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            parentFlow.decision = { state.value?.decision }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            return runCatching { wm.addView(root, params) }
                .onFailure { Log.w(TAG, "Couldn't show the lock overlay", it) }
                .isSuccess
        }

        fun dismiss() {
            runCatching { wm.removeViewImmediate(root) }
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
            scope.cancel()
        }
    }

    private companion object {
        const val TAG = "LockOverlay"
    }
}
