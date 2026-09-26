package com.nourtime.app.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nourtime.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase for Phase 2. Nothing here runs until parent mode or pairing asks for it, so Phase 1
 * stays offline. Debug builds talk to the local Firebase Emulator Suite
 * (`firebase/`, `npm run emulators`) through `adb reverse tcp:9099 tcp:9099` and `tcp:8080`.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private const val AUTH_EMULATOR_PORT = 9099
    private const val FIRESTORE_EMULATOR_PORT = 8080

    val usesEmulator: Boolean get() = BuildConfig.FIREBASE_EMULATOR_HOST.isNotEmpty()

    @Provides
    @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance().also {
        if (usesEmulator) it.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, AUTH_EMULATOR_PORT)
    }

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().also {
        if (usesEmulator) it.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
    }
}
