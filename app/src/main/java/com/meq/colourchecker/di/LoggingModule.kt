package com.meq.colourchecker.di

import com.meq.colourchecker.BuildConfig
import com.meq.colourchecker.util.Logger
import com.meq.colourchecker.util.NoOpLogger
import com.meq.colourchecker.util.TimberLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return if (BuildConfig.DEBUG) {
            TimberLogger()
        } else {
            // Keep release output quiet; replace with crash reporting when available.
            NoOpLogger()
        }
    }
}
