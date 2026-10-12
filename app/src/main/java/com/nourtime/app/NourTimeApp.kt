package com.nourtime.app

import android.app.Application
import android.content.res.Configuration
import com.nourtime.app.core.locale.AppLocales
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nourtime.app.core.crash.CrashReportingController
import com.nourtime.app.data.backup.SettingsBackup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class NourTimeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // The parent's language choice (older phones; Android 13+ does this itself).
        AppLocales.applyToApp(this)
        // Off the main thread: a slow start at boot got Nour Time killed ("failed to complete startup").
        // The app, Accessibility service and timer share this one process, so the copy follows every change.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val start = EntryPointAccessors.fromApplication(this@NourTimeApp, AppStartEntryPoint::class.java)
            launch(Dispatchers.IO) {
                runCatching { start.settingsBackup().mirror(start.settingsStore()) }
                    .onFailure { if (it !is CancellationException) Log.w("NourTimeApp", "settings backup stopped", it) }
            }
            runCatching { start.crashReporting().run() }
                .onFailure { if (it !is CancellationException) Log.w("NourTimeApp", "crash reporting setup stopped", it) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // A system change (rotation, dark mode) resets the shared resources to the phone's language.
        AppLocales.applyToApp(this)
    }
}

/** What the app start needs, fetched on a background thread instead of injected on the main one. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppStartEntryPoint {
    fun settingsStore(): DataStore<Preferences>
    fun settingsBackup(): SettingsBackup
    fun crashReporting(): CrashReportingController
}
