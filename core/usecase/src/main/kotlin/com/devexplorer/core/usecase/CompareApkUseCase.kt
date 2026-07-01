package com.devexplorer.core.usecase

import com.devexplorer.core.model.ApkDiff
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.diffApks

/**
 * Analyzes two APKs (each from any [StorageRef] source) and produces the
 * structural [ApkDiff] between them. Both analyses reuse [AnalyzeApkUseCase], so
 * the same read-only guarantee applies to each side.
 *
 * The result carries the two [ApkSummary] snapshots alongside the diff so the UI
 * can label each side and offer a shareable report without re-analyzing.
 * Wrapped in [Result] so a corrupt/unreadable archive on either side surfaces as
 * UI state, not a crash.
 */
class CompareApkUseCase(
    private val analyzeApk: AnalyzeApkUseCase,
) {
    data class Comparison(
        val old: ApkSummary,
        val new: ApkSummary,
        val diff: ApkDiff,
    )

    suspend operator fun invoke(old: StorageRef, new: StorageRef): Result<Comparison> = runCatching {
        val oldSummary = analyzeApk(old).getOrThrow()
        val newSummary = analyzeApk(new).getOrThrow()
        Comparison(oldSummary, newSummary, diffApks(oldSummary, newSummary))
    }
}
