package com.devexplorer.data.db

import com.devexplorer.core.capability.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [FavoritesRepository]. */
class RoomFavoritesRepository(
    private val dao: FavoritePackageDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : FavoritesRepository {

    override fun observe(): Flow<Set<String>> =
        dao.observe().map { rows -> rows.map { it.packageName }.toSet() }

    override suspend fun toggle(packageName: String) {
        val existing = dao.find(packageName)
        if (existing == null) {
            dao.upsert(FavoritePackageEntity(packageName = packageName, addedAt = clock()))
        } else {
            dao.delete(packageName)
        }
    }
}
