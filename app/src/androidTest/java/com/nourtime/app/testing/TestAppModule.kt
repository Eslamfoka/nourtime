package com.nourtime.app.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.AndroidDeviceClock
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.timer.TimekeepingMigration
import com.nourtime.app.core.timer.TimekeepingStore
import com.nourtime.app.data.backup.SettingsBackup
import com.nourtime.app.data.db.NourDatabase
import com.nourtime.app.di.AppModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID
import javax.inject.Singleton

/**
 * [AppModule] for instrumented tests: the same stores, but on files of their own for each test. A
 * DataStore must be the only one open on its file (each test builds a new component), and no test
 * sees another's settings or timer. The database is in memory.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    /** One id per test's component, shared by its files. */
    @Provides
    @Singleton
    fun testFiles(): TestFiles = TestFiles(UUID.randomUUID().toString().take(8))

    @Provides
    @Singleton
    fun provideSettingsBackup(@ApplicationContext context: Context, files: TestFiles): SettingsBackup =
        SettingsBackup(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(files.name("prefs_backup")) },
            ),
        )

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context, backup: SettingsBackup, files: TestFiles): DataStore<Preferences> =
        SettingsBackup.mainStore(backup, CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            context.preferencesDataStoreFile(files.name("prefs"))
        }

    @Provides
    @Singleton
    @TimekeepingStore
    fun provideTimekeepingStore(@ApplicationContext context: Context, main: DataStore<Preferences>, files: TestFiles): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { TimekeepingMigration.onCorruption() },
            migrations = listOf(TimekeepingMigration(main)),
            produceFile = { context.preferencesDataStoreFile(files.name("timer")) },
        )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NourDatabase =
        Room.inMemoryDatabaseBuilder(context, NourDatabase::class.java).build()

    // Fewer iterations than the real 120k: the tests check behaviour, not hashing cost.
    @Provides
    @Singleton
    fun provideSecretHasher(): SecretHasher = SecretHasher(iterations = 1_000)

    @Provides
    @Singleton
    fun provideDeviceClock(@ApplicationContext context: Context): DeviceClock = AndroidDeviceClock(context)
}

/** Names this test's data files, all starting with [PREFIX] so a run can clear them. */
class TestFiles(private val id: String) {
    fun name(base: String) = "$PREFIX${id}_$base"

    companion object {
        const val PREFIX = "itest_"
    }
}
