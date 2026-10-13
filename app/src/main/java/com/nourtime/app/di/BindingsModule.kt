package com.nourtime.app.di

import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.core.time.WallClock
import com.nourtime.app.data.usage.UsageRepository
import com.nourtime.app.data.usage.UsageSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Interfaces the timer depends on, so it can be tested without Android. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun wallClock(clock: TrustedClock): WallClock

    @Binds
    abstract fun usageSink(usage: UsageRepository): UsageSink
}
