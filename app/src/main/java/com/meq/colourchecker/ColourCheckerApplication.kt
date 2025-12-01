package com.meq.colourchecker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ColourCheckerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Silence Timber in release; swap for crash reporting when available.
            Timber.plant(NoOpTree)
        }
    }

    private object NoOpTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) = Unit
    }
}
