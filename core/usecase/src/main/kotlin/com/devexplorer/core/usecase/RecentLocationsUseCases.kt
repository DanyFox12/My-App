package com.devexplorer.core.usecase

import com.devexplorer.core.capability.RecentLocationsRepository
import com.devexplorer.core.model.RecentLocation
import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.flow.Flow

/** Reactive stream of recently opened folders (newest first). */
class ObserveRecentLocationsUseCase(
    private val repository: RecentLocationsRepository,
) {
    operator fun invoke(): Flow<List<RecentLocation>> = repository.observe()
}

/** Record (or refresh) a folder in the recents history. */
class RecordRecentLocationUseCase(
    private val repository: RecentLocationsRepository,
) {
    suspend operator fun invoke(ref: StorageRef, label: String) = repository.record(ref, label)
}

/** Remove a folder from the recents history. */
class RemoveRecentLocationUseCase(
    private val repository: RecentLocationsRepository,
) {
    suspend operator fun invoke(ref: StorageRef) = repository.remove(ref)
}
