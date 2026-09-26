package com.ridevibe.core.network.log

import android.util.Log
import com.ridevibe.core.network.BuildConfig

/**
 * Logcat under one tag, safe to call from JVM unit tests: in local tests
 * `android.util.Log` is the SDK stub that throws (this module does not set
 * `unitTests.isReturnDefaultValues`), so every call is guarded. Never log
 * request bodies, names or tokens through here — see the DEBUG-only
 * logging interceptor in NetworkModule for wire traces.
 */
internal object RideVibeLog {
    const val TAG = "RideVibe"

    fun w(message: String, error: Throwable? = null) = safely { Log.w(TAG, message, error) }

    fun d(message: String) = safely { if (BuildConfig.DEBUG) Log.d(TAG, message) }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // Stubbed Android framework (JVM unit test) — logging is best-effort.
        }
    }
}
