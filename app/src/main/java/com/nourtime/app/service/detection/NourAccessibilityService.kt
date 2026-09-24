package com.nourtime.app.service.detection

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Detects which app is in the foreground so the timer only runs while a selected app is open.
 * Foreground-app tracking is added in step 2; for now the service only needs to exist so the
 * parent can enable it during onboarding.
 */
class NourAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
