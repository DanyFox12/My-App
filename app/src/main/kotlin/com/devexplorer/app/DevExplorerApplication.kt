package com.devexplorer.app

import android.app.Application
import com.devexplorer.data.work.WorkScheduler

/**
 * Application entry point. Schedules the app's periodic background work once at
 * process start. Declared in the manifest via android:name=".DevExplorerApplication".
 */
class DevExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Idempotent (ExistingPeriodicWorkPolicy.KEEP) — safe to call every launch.
        WorkScheduler.schedulePeriodicCleanup(this)
    }
}
