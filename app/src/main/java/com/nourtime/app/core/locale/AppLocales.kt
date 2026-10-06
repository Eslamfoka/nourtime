package com.nourtime.app.core.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies [AppLanguage] everywhere, on every Android version:
 * - Android 13+: the system's per-app language (it also shows in the phone's settings and applies to
 *   services, notifications and the lock screen).
 * - Older phones: the choice is kept here and applied to the app's shared resources (which the
 *   services and the lock overlay use) and to each activity.
 */
object AppLocales {
    private const val PREFS = "nour_language"
    private const val KEY = "language"

    /** The phone's own locale, remembered before the app overrides the default. */
    private var phoneLocale: Locale? = null

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].toLanguageTag())
        } else {
            AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        }

    /** Saves and applies the choice. On older phones the activity is recreated to show it. */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system saves it and recreates the activities itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).commit()
            applyToApp(activity.applicationContext)
            activity.recreate()
        }
    }

    /** Older phones: call when the app starts and after any configuration change. */
    fun applyToApp(appContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val locale = locale(appContext)
        Locale.setDefault(locale)
        updateResources(appContext.resources, locale)
    }

    /** Older phones: wraps an activity's base context so its screens use the choice. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale(base))
        return base.createConfigurationContext(config)
    }

    /** The language Nour Time shows: the choice, or the phone's own when it follows the phone. */
    fun locale(context: Context): Locale {
        val phone = phoneLocale ?: Resources.getSystem().configuration.locales[0].also { phoneLocale = it }
        return current(context).localeOr(phone)
    }

    @Suppress("DEPRECATION") // The shared resources of services and overlays have no other way on API < 33.
    private fun updateResources(resources: Resources, locale: Locale) {
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
