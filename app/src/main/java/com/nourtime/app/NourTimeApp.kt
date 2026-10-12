package com.nourtime.app

import android.app.Application
import android.content.res.Configuration
import com.nourtime.app.core.locale.AppLocales
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nourtime.app.data.backup.SettingsBackup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NourTimeApp : Application() {

    @Inject lateinit var settingsStore: DataStore<Preferences>
    @Inject lateinit var settingsBackup: SettingsBackup

    override fun onCreate() {
        super.onCreate()
        // The parent's language choice (older phones; Android 13+ does this itself).
        AppLocales.applyToApp(this)
        // The app, Accessibility service and timer share this one process, so the copy follows every change.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { settingsBackup.mirror(settingsStore) }
                .onFailure { if (it !is CancellationException) Log.w("NourTimeApp", "settings backup stopped", it) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A system change (rotation, dark mode) resets the shared resources to the phone's language.
        AppLocales.applyToApp(this)
    }
}
