package com.devexplorer.core.usecase

import com.devexplorer.core.capability.FavoritesRepository
import kotlinx.coroutines.flow.Flow

/** Reactive read of the pinned package names. */
class ObserveFavoritesUseCase(
    private val favorites: FavoritesRepository,
) {
    operator fun invoke(): Flow<Set<String>> = favorites.observe()
}

/** Pin/unpin one package in the Packages list. */
class ToggleFavoriteUseCase(
    private val favorites: FavoritesRepository,
) {
    suspend operator fun invoke(packageName: String): Result<Unit> = runCatching {
        require(packageName.isNotBlank()) { "A package name is required." }
        favorites.toggle(packageName)
    }
}
