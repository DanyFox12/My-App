package com.devexplorer.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the app's periodic background work. Called once from the Application.
 */
object WorkScheduler {

    fun schedulePeriodicCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                // Be a good citizen: only run when the battery isn't low.
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        // KEEP: don't reset the schedule if it already exists from a prior launch.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private const val UNIQUE_NAME = "cache_cleanup"
}
