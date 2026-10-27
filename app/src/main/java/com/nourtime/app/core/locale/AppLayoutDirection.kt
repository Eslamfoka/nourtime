package com.nourtime.app.core.locale

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.layoutDirection
import java.util.Locale

/**
 * Lays out [content] in [locale]'s direction. On Android 12 and older a window keeps the phone's direction
 * when Nour Time shows another language (Arabic text laid out left to right, or English right to left):
 * the app's screens and the lock overlay both go through this.
 */
@Composable
fun AppLayoutDirection(locale: Locale, content: @Composable () -> Unit) {
    val rtl = locale.layoutDirection == View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
}
