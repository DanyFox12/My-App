package com.devexplorer.app.feature.packages

import com.devexplorer.core.model.InstalledPackage

/** Immutable UI state for the installed-packages list. */
data class PackagesUiState(
    val isLoading: Boolean = false,
    val allPackages: List<InstalledPackage> = emptyList(),
    val query: String = "",
    val includeSystem: Boolean = false,
    val errorMessage: String? = null,
    /** Package names the user pinned (Room-backed, reactive). */
    val favorites: Set<String> = emptySet(),
    val onlyFavorites: Boolean = false,
) {
    /** Packages after search/favorites filters, pinned apps first (stable order). */
    val visible: List<InstalledPackage>
        get() {
            val searched = if (query.isBlank()) {
                allPackages
            } else {
                allPackages.filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
            val filtered = if (onlyFavorites) {
                searched.filter { it.packageName in favorites }
            } else {
                searched
            }
            return filtered.sortedByDescending { it.packageName in favorites }
        }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && visible.isEmpty()
}

sealed interface PackagesEvent {
    data object Refresh : PackagesEvent
    data class SetQuery(val query: String) : PackagesEvent
    data class SetIncludeSystem(val include: Boolean) : PackagesEvent
    data class SetOnlyFavorites(val only: Boolean) : PackagesEvent
    data class ToggleFavorite(val packageName: String) : PackagesEvent
}
