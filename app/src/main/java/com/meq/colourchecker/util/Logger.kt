package com.meq.colourchecker.util

import timber.log.Timber

/**
 * Logging interface for dependency injection and testing.
 */
interface Logger {
    fun d(message: String, vararg args: Any?)
    fun i(message: String, vararg args: Any?)
    fun w(message: String, vararg args: Any?)
    fun e(message: String, throwable: Throwable? = null, vararg args: Any?)
}

/**
 * Timber-based logger implementation.
 */
class TimberLogger : Logger {
    override fun d(message: String, vararg args: Any?) {
        Timber.d(message, *args)
    }

    override fun i(message: String, vararg args: Any?) {
        Timber.i(message, *args)
    }

    override fun w(message: String, vararg args: Any?) {
        Timber.w(message, *args)
    }

    override fun e(message: String, throwable: Throwable?, vararg args: Any?) {
        if (throwable != null) {
            Timber.e(throwable, message, *args)
        } else {
            Timber.e(message, *args)
        }
    }
}

/**
 * No-op logger for testing or release builds.
 */
class NoOpLogger : Logger {
    override fun d(message: String, vararg args: Any?) {}
    override fun i(message: String, vararg args: Any?) {}
    override fun w(message: String, vararg args: Any?) {}
    override fun e(message: String, throwable: Throwable?, vararg args: Any?) {}
}
