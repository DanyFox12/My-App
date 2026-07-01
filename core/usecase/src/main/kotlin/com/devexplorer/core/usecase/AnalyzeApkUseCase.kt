package com.devexplorer.core.usecase

import com.devexplorer.core.capability.ApkRepository
import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.StorageRef

/**
 * Analyzes the APK at [StorageRef]: opens a read-only stream via the storage
 * layer (SAF) and hands it to the [ApkRepository]. Both sides are read-only; the
 * source archive is never touched. Wrapped in [Result] so a corrupt or
 * unreadable archive surfaces as UI state, not a crash.
 */
class AnalyzeApkUseCase(
    private val storage: StorageRepository,
    private val apk: ApkRepository,
) {
    suspend operator fun invoke(source: StorageRef): Result<ApkSummary> = runCatching {
        storage.openInputStream(source).use { input ->
            apk.analyze(input)
        }
    }
}
