package com.ridevibe.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RideVibeApplication : Application() {

    override fun onCreate() {
        // Before super.onCreate so Hilt's own startup work is covered too.
        if (isDebuggable) enableStrictMode()
        super.onCreate()
        createNotificationChannels()
    }

    /** Debug builds only; read from the manifest flag because the app module generates no BuildConfig. */
    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Debug only, log only: surfaces main-thread disk/network access and leaked
     * closeables in Logcat without killing the app. Penalties stay off in
     * release, where a stray violation must never crash a rider.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }

    /**
     * The "trips" channel (departure reminders, delay notices) exists from first
     * launch so a future push integration has a home and the rider can already
     * tune it in system settings. No FCM yet: nothing posts to it today.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            TRIPS_CHANNEL_ID,
            "Trips",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Departure reminders and changes to your booked trips"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val TRIPS_CHANNEL_ID = "trips"
    }
}
