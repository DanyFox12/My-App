package com.devexplorer.app.feature.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.WorkspaceItem
import com.devexplorer.core.usecase.DeleteWorkspaceItemUseCase
import com.devexplorer.core.usecase.ListWorkspaceUseCase
import com.devexplorer.core.usecase.RenameWorkspaceItemUseCase
import com.devexplorer.data.workspace.WorkspaceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Workspace. Lists the sandbox and performs the mutating operations
 * (rename/delete) through use-cases that each carry a [WriteCapability] minted by
 * :data:workspace. The sandbox is re-listed after every change and whenever the
 * tab is shown (the Explorer may have copied something in).
 */
class WorkspaceViewModel(
    private val listWorkspace: ListWorkspaceUseCase,
    private val renameItem: RenameWorkspaceItemUseCase,
    private val deleteItem: DeleteWorkspaceItemUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: WorkspaceEvent) {
        when (event) {
            WorkspaceEvent.Refresh -> refresh()
            is WorkspaceEvent.RequestRename -> _uiState.update { it.copy(renameTarget = event.item) }
            is WorkspaceEvent.ConfirmRename -> onConfirmRename(event.item, event.newName)
            is WorkspaceEvent.RequestDelete -> _uiState.update { it.copy(deleteTarget = event.item) }
            is WorkspaceEvent.ConfirmDelete -> onConfirmDelete(event.item)
            WorkspaceEvent.DismissDialog ->
                _uiState.update { it.copy(renameTarget = null, deleteTarget = null) }
            WorkspaceEvent.ConsumeMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            listWorkspace()
                .onSuccess { items ->
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't read the Workspace.")
                    }
                }
        }
    }

    private fun onConfirmRename(item: WorkspaceItem, newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(renameTarget = null) }
            renameItem(item, newName)
                .onSuccess { refreshWith("Renamed to \"${it.name}\"") }
                .onFailure { _uiState.update { s -> s.copy(message = "Rename failed") } }
        }
    }

    private fun onConfirmDelete(item: WorkspaceItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(deleteTarget = null) }
            deleteItem(item)
                .onSuccess { refreshWith("Deleted \"${item.name}\"") }
                .onFailure { _uiState.update { s -> s.copy(message = "Delete failed") } }
        }
    }

    private fun refreshWith(message: String) {
        _uiState.update { it.copy(message = message) }
        refresh()
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repo = WorkspaceModule.repository(appContext)
                val writeToken = WorkspaceModule.writeCapability()
                WorkspaceViewModel(
                    listWorkspace = ListWorkspaceUseCase(repo),
                    renameItem = RenameWorkspaceItemUseCase(repo, writeToken),
                    deleteItem = DeleteWorkspaceItemUseCase(repo, writeToken),
                )
            }
        }
    }
}
