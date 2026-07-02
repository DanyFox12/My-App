package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * A compact, persistable record of one APK analysis — just the facts worth
 * diffing when the same package is analyzed again after an update. Stored in
 * Room (:data:db); the full [ApkSummary] is deliberately NOT persisted.
 */
@Serializable
data class AnalysisSnapshot(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val analyzedAt: Long,
    val uncompressedBytes: Long,
    val entryCount: Int,
    val permissions: List<String>,
    val dexMethodRefs: Int,
    val dexClasses: Int,
)

/**
 * Distill a summary into its snapshot, or null when the archive isn't an
 * identifiable package (no package name / version code — nothing to track).
 */
fun snapshotOf(summary: ApkSummary, analyzedAt: Long): AnalysisSnapshot? {
    val packageName = summary.packageName ?: return null
    val versionCode = summary.versionCode ?: return null
    return AnalysisSnapshot(
        packageName = packageName,
        versionName = summary.versionName,
        versionCode = versionCode,
        analyzedAt = analyzedAt,
        uncompressedBytes = summary.totalUncompressedBytes,
        entryCount = summary.entryCount,
        permissions = summary.permissions.sorted(),
        dexMethodRefs = summary.dexStats?.totalMethods ?: 0,
        dexClasses = summary.dexStats?.totalClasses ?: 0,
    )
}

/**
 * What changed between two snapshots of the same package — the answer to
 * "this app just updated; what did the update actually do?". Pure and
 * unit-tested; the UI renders it as a "changed since vX" card.
 */
@Serializable
data class SnapshotDelta(
    val previous: AnalysisSnapshot,
    val current: AnalysisSnapshot,
) {
    val sizeDeltaBytes: Long get() = current.uncompressedBytes - previous.uncompressedBytes
    val entryDelta: Int get() = current.entryCount - previous.entryCount
    val methodRefsDelta: Int get() = current.dexMethodRefs - previous.dexMethodRefs
    val addedPermissions: List<String> get() = current.permissions - previous.permissions.toSet()
    val removedPermissions: List<String> get() = previous.permissions - current.permissions.toSet()

    val hasChanges: Boolean
        get() = sizeDeltaBytes != 0L || entryDelta != 0 || methodRefsDelta != 0 ||
            addedPermissions.isNotEmpty() || removedPermissions.isNotEmpty()
}

/**
 * Delta between the latest recorded snapshot and a fresh one, or null when
 * they aren't comparable (different package) or the version didn't change
 * (re-analyzing the same build isn't an update).
 */
fun snapshotDelta(previous: AnalysisSnapshot?, current: AnalysisSnapshot): SnapshotDelta? {
    if (previous == null) return null
    if (previous.packageName != current.packageName) return null
    if (previous.versionCode == current.versionCode) return null
    return SnapshotDelta(previous = previous, current = current)
}
