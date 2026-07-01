package com.devexplorer.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes stale temp files the APK analyzer stages in `cacheDir`. They're already
 * removed in a `finally`, so this is a safety net (e.g. if the process was killed
 * mid-analysis).
 *
 * As a [CoroutineWorker] its `doWork` is a suspend function run on a background
 * executor; WorkManager persists the request in its own SQLite DB and runs it
 * subject to constraints, surviving process death and reboot. Work must be
 * **idempotent** — deleting already-clean files is harmless, so re-runs are safe.
 */
class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
            applicationContext.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(TEMP_PREFIX) && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private companion object {
        const val TEMP_PREFIX = "analyze_"
        const val MAX_AGE_MILLIS = 60L * 60L * 1000L // 1 hour
    }
}
