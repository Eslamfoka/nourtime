package com.nourtime.app

import android.app.Application
import android.content.res.Configuration
import com.nourtime.app.core.locale.AppLocales
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NourTimeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The parent's language choice (older phones; Android 13+ does this itself).
        AppLocales.applyToApp(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A system change (rotation, dark mode) resets the shared resources to the phone's language.
        AppLocales.applyToApp(this)
    }
}
