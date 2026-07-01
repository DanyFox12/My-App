package com.devexplorer.core.usecase

import com.devexplorer.core.capability.ApkRepository
import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.StorageRef

/**
 * Analyzes an APK from either source, behind one [StorageRef]:
 *  - a **file** (SAF document) → read via [StorageRepository]
 *  - an **installed package** → read its base APK via [PackagesRepository]
 *
 * Both paths yield a read-only [java.io.InputStream] handed to the same
 * [ApkRepository]. The source is never modified. Wrapped in [Result] so a
 * corrupt/unreadable archive surfaces as UI state, not a crash.
 */
class AnalyzeApkUseCase(
    private val storage: StorageRepository,
    private val packages: PackagesRepository,
    private val apk: ApkRepository,
) {
    suspend operator fun invoke(source: StorageRef): Result<ApkSummary> = runCatching {
        val input = when (source.kind) {
            StorageRef.Kind.InstalledPackage -> packages.openApk(source.raw)
            else -> storage.openInputStream(source)
        }
        input.use { apk.analyze(it) }
    }
}
