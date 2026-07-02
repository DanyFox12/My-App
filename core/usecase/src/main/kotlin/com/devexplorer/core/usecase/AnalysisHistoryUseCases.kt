package com.devexplorer.core.usecase

import com.devexplorer.core.capability.AnalysisHistoryRepository
import com.devexplorer.core.model.AnalysisSnapshot
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.SnapshotDelta
import com.devexplorer.core.model.snapshotDelta
import com.devexplorer.core.model.snapshotOf
import kotlinx.coroutines.flow.Flow

/**
 * Records the snapshot of a fresh analysis and answers "what changed since the
 * version I looked at last time?". [analyzedAt] is supplied by the caller so
 * the domain stays clock-free. Success with null means "nothing to compare" —
 * an unidentifiable archive, a first-ever analysis, or an unchanged version.
 */
class TrackAnalysisUseCase(
    private val history: AnalysisHistoryRepository,
) {
    suspend operator fun invoke(summary: ApkSummary, analyzedAt: Long): Result<SnapshotDelta?> =
        runCatching {
            val snapshot = snapshotOf(summary, analyzedAt) ?: return@runCatching null
            val latest = history.latestFor(snapshot.packageName)
            // Re-analyzing the same version must not add journal rows.
            if (latest == null || latest.versionCode != snapshot.versionCode) {
                history.record(snapshot)
            }
            // Diff against the newest *different* build so the "what changed"
            // card survives repeated viewings of the same version.
            val baseline = history.latestOtherVersion(snapshot.packageName, snapshot.versionCode)
            snapshotDelta(baseline, snapshot)
        }
}

/** Reactive read of the recorded history, newest first. */
class ObserveAnalysisHistoryUseCase(
    private val history: AnalysisHistoryRepository,
) {
    operator fun invoke(): Flow<List<AnalysisSnapshot>> = history.observe()
}
