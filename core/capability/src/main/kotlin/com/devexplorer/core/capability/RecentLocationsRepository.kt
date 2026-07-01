package com.devexplorer.core.capability

import com.devexplorer.core.model.RecentLocation
import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.flow.Flow

/**
 * Persists the folders the user has opened (local history). Backed by Room in
 * :data:db. [observe] is reactive — the UI updates automatically when a new
 * location is recorded, with no manual refresh.
 */
interface RecentLocationsRepository {
    fun observe(): Flow<List<RecentLocation>>
    suspend fun record(ref: StorageRef, label: String)
    suspend fun remove(ref: StorageRef)
}
