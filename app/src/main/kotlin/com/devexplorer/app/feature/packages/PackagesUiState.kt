package com.devexplorer.app.feature.packages

import com.devexplorer.core.model.InstalledPackage

/** Immutable UI state for the installed-packages list. */
data class PackagesUiState(
    val isLoading: Boolean = false,
    val allPackages: List<InstalledPackage> = emptyList(),
    val query: String = "",
    val includeSystem: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Packages after applying the search filter (label or package name). */
    val visible: List<InstalledPackage>
        get() = if (query.isBlank()) {
            allPackages
        } else {
            allPackages.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && visible.isEmpty()
}

sealed interface PackagesEvent {
    data object Refresh : PackagesEvent
    data class SetQuery(val query: String) : PackagesEvent
    data class SetIncludeSystem(val include: Boolean) : PackagesEvent
}
