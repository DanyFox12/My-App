package com.devexplorer.app.feature.packages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.usecase.ListInstalledPackagesUseCase
import com.devexplorer.core.usecase.ObserveFavoritesUseCase
import com.devexplorer.core.usecase.ToggleFavoriteUseCase
import com.devexplorer.data.db.DbModule
import com.devexplorer.data.packages.PackageManagerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Packages list. Loading (and the system-app filter) go through the
 * repository; search filtering is derived in the UI state. System apps are
 * excluded by default because the list is long and user apps are the common case.
 * Pinned packages come from the Room-backed favorites store and sort first.
 */
class PackagesViewModel(
    private val listPackages: ListInstalledPackagesUseCase,
    observeFavorites: ObserveFavoritesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PackagesUiState())
    val uiState: StateFlow<PackagesUiState> = _uiState.asStateFlow()

    init {
        load(includeSystem = false)
        observeFavorites()
            .onEach { favorites -> _uiState.update { it.copy(favorites = favorites) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: PackagesEvent) {
        when (event) {
            PackagesEvent.Refresh -> load(_uiState.value.includeSystem)
            is PackagesEvent.SetQuery -> _uiState.update { it.copy(query = event.query) }
            is PackagesEvent.SetIncludeSystem -> load(event.include)
            is PackagesEvent.SetOnlyFavorites -> _uiState.update { it.copy(onlyFavorites = event.only) }
            is PackagesEvent.ToggleFavorite -> viewModelScope.launch {
                toggleFavorite(event.packageName)
            }
        }
    }

    private fun load(includeSystem: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, includeSystem = includeSystem) }
            listPackages(includeSystem)
                .onSuccess { packages ->
                    _uiState.update { it.copy(isLoading = false, allPackages = packages) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't read installed packages.")
                    }
                }
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repo = PackageManagerRepository(appContext)
                val favorites = DbModule.favoritesRepository(appContext)
                PackagesViewModel(
                    listPackages = ListInstalledPackagesUseCase(repo),
                    observeFavorites = ObserveFavoritesUseCase(favorites),
                    toggleFavorite = ToggleFavoriteUseCase(favorites),
                )
            }
        }
    }
}
