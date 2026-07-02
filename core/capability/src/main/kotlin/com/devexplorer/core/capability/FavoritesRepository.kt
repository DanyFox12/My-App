package com.devexplorer.core.capability

import kotlinx.coroutines.flow.Flow

/**
 * Persists the user's pinned packages (backed by Room in :data:db). Purely
 * local bookkeeping about *our* UI — it never touches the packages themselves.
 */
interface FavoritesRepository {

    /** Package names currently pinned, reactive. */
    fun observe(): Flow<Set<String>>

    /** Pin the package if unpinned, unpin it otherwise. */
    suspend fun toggle(packageName: String)
}
