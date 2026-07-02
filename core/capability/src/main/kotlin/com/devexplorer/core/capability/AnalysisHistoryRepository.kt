package com.devexplorer.core.capability

import com.devexplorer.core.model.AnalysisSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Persists compact [AnalysisSnapshot]s of analyzed packages (local history,
 * backed by Room in :data:db). Recording keeps only the most recent snapshots
 * per package — this is a change journal, not an archive. Reading is reactive
 * via [observe], same contract as [RecentLocationsRepository].
 */
interface AnalysisHistoryRepository {

    /** Most recent snapshots across all packages, newest first. */
    fun observe(): Flow<List<AnalysisSnapshot>>

    /** The latest recorded snapshot for one package, or null if never analyzed. */
    suspend fun latestFor(packageName: String): AnalysisSnapshot?

    /**
     * The latest snapshot of a *different* build of the package — the baseline
     * for "what changed in this update", stable across repeated viewings.
     */
    suspend fun latestOtherVersion(packageName: String, versionCode: Long): AnalysisSnapshot?

    /** Record a new snapshot and prune old ones for the same package. */
    suspend fun record(snapshot: AnalysisSnapshot)
}
