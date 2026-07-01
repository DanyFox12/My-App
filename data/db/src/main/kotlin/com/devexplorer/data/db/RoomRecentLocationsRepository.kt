package com.devexplorer.data.db

import com.devexplorer.core.capability.RecentLocationsRepository
import com.devexplorer.core.model.RecentLocation
import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [RecentLocationsRepository]. Maps between the domain
 * [RecentLocation] and the [RecentLocationEntity], and stamps the open time from
 * the injected [clock] (kept injectable so the mapping stays unit-testable).
 */
class RoomRecentLocationsRepository(
    private val dao: RecentLocationDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RecentLocationsRepository {

    override fun observe(): Flow<List<RecentLocation>> =
        dao.observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(ref: StorageRef, label: String) {
        dao.upsert(
            RecentLocationEntity(
                refRaw = ref.raw,
                kind = ref.kind.name,
                label = label,
                lastOpenedAt = clock(),
            ),
        )
    }

    override suspend fun remove(ref: StorageRef) {
        dao.delete(ref.raw)
    }

    private fun RecentLocationEntity.toDomain(): RecentLocation = RecentLocation(
        ref = StorageRef(raw = refRaw, kind = StorageRef.Kind.valueOf(kind)),
        label = label,
        lastOpenedAt = lastOpenedAt,
    )
}
