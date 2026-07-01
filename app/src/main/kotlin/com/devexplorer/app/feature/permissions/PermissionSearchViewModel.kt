package com.devexplorer.app.feature.permissions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.PermissionUsage
import com.devexplorer.core.usecase.GetPermissionUsageUseCase
import com.devexplorer.data.packages.PackageManagerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionSearchUiState(
    val isLoading: Boolean = false,
    val all: List<PermissionUsage> = emptyList(),
    val query: String = "",
    val includeSystem: Boolean = false,
    val expanded: Set<String> = emptySet(),
    val errorMessage: String? = null,
) {
    val visible: List<PermissionUsage>
        get() = if (query.isBlank()) all
        else all.filter { it.permission.contains(query, ignoreCase = true) }

    val isEmpty: Boolean get() = !isLoading && errorMessage == null && visible.isEmpty()
}

sealed interface PermissionSearchEvent {
    data class SetQuery(val query: String) : PermissionSearchEvent
    data class SetIncludeSystem(val include: Boolean) : PermissionSearchEvent
    data class ToggleExpand(val permission: String) : PermissionSearchEvent
    data object Refresh : PermissionSearchEvent
}

class PermissionSearchViewModel(
    private val getPermissionUsage: GetPermissionUsageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionSearchUiState())
    val uiState: StateFlow<PermissionSearchUiState> = _uiState.asStateFlow()

    init {
        load(includeSystem = false)
    }

    fun onEvent(event: PermissionSearchEvent) {
        when (event) {
            is PermissionSearchEvent.SetQuery -> _uiState.update { it.copy(query = event.query) }
            is PermissionSearchEvent.SetIncludeSystem -> load(event.include)
            is PermissionSearchEvent.ToggleExpand -> _uiState.update {
                val next = it.expanded.toMutableSet()
                if (!next.add(event.permission)) next.remove(event.permission)
                it.copy(expanded = next)
            }
            PermissionSearchEvent.Refresh -> load(_uiState.value.includeSystem)
        }
    }

    private fun load(includeSystem: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, includeSystem = includeSystem, expanded = emptySet())
            }
            getPermissionUsage(includeSystem)
                .onSuccess { usage -> _uiState.update { it.copy(isLoading = false, all = usage) } }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Couldn't read permissions.") }
                }
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repo = PackageManagerRepository(appContext)
                PermissionSearchViewModel(GetPermissionUsageUseCase(repo))
            }
        }
    }
}
