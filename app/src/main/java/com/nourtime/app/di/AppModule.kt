package com.nourtime.app.di

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
import com.nourtime.app.data.db.NourDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A damaged settings file would otherwise crash every start and leave the phone unprotected.
            // Starting fresh sends the parent back through setup instead.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile("nour_prefs") },
        )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NourDatabase =
        Room.databaseBuilder(context, NourDatabase::class.java, "nour.db").build()

    @Provides
    @Singleton
    fun provideSecretHasher(): SecretHasher = SecretHasher()

    @Provides
    @Singleton
    fun provideDeviceClock(@ApplicationContext context: Context): DeviceClock = AndroidDeviceClock(context)
}
