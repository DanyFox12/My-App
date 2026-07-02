package com.devexplorer.data.db

import com.devexplorer.core.capability.AnalysisHistoryRepository
import com.devexplorer.core.model.AnalysisSnapshot

/** Room-backed [AnalysisHistoryRepository]; keeps the last [KEEP_PER_PACKAGE] rows per package. */
class RoomAnalysisHistoryRepository(
    private val dao: AnalysisSnapshotDao,
) : AnalysisHistoryRepository {

    override suspend fun latestFor(packageName: String): AnalysisSnapshot? =
        dao.latestFor(packageName)?.toDomain()

    override suspend fun latestOtherVersion(packageName: String, versionCode: Long): AnalysisSnapshot? =
        dao.latestOtherVersion(packageName, versionCode)?.toDomain()

    override suspend fun record(snapshot: AnalysisSnapshot) {
        dao.insert(snapshot.toEntity())
        dao.prune(snapshot.packageName, KEEP_PER_PACKAGE)
    }

    private fun AnalysisSnapshotEntity.toDomain() = AnalysisSnapshot(
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        analyzedAt = analyzedAt,
        uncompressedBytes = uncompressedBytes,
        entryCount = entryCount,
        permissions = if (permissions.isEmpty()) emptyList() else permissions.split('\n'),
        dexMethodRefs = dexMethodRefs,
        dexClasses = dexClasses,
    )

    private fun AnalysisSnapshot.toEntity() = AnalysisSnapshotEntity(
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        analyzedAt = analyzedAt,
        uncompressedBytes = uncompressedBytes,
        entryCount = entryCount,
        permissions = permissions.joinToString("\n"),
        dexMethodRefs = dexMethodRefs,
        dexClasses = dexClasses,
    )

    private companion object {
        const val KEEP_PER_PACKAGE = 5
    }
}
