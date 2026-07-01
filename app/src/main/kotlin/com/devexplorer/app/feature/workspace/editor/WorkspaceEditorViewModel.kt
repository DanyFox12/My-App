package com.devexplorer.app.feature.workspace.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.WorkspaceItem
import com.devexplorer.core.usecase.ReadWorkspaceTextUseCase
import com.devexplorer.core.usecase.SaveWorkspaceTextUseCase
import com.devexplorer.data.workspace.WorkspaceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceEditorUiState(
    val isLoading: Boolean = true,
    val content: String = "",
    val savedContent: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    /** True when there are unsaved edits — drives the Save action's enabled state. */
    val isDirty: Boolean get() = content != savedContent
}

sealed interface WorkspaceEditorEvent {
    data class Edit(val content: String) : WorkspaceEditorEvent
    data object Save : WorkspaceEditorEvent
    data object ConsumeMessage : WorkspaceEditorEvent
}

/**
 * Loads a Workspace file's text and saves edits back to it. This is the app's
 * only in-place editor, and it writes exclusively through
 * [SaveWorkspaceTextUseCase], which carries a [com.devexplorer.core.capability.WriteCapability]
 * minted by :data:workspace — so editing stays confined to the sandbox.
 */
class WorkspaceEditorViewModel(
    private val readText: ReadWorkspaceTextUseCase,
    private val saveText: SaveWorkspaceTextUseCase,
    private val item: WorkspaceItem,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceEditorUiState())
    val uiState: StateFlow<WorkspaceEditorUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: WorkspaceEditorEvent) {
        when (event) {
            is WorkspaceEditorEvent.Edit -> _uiState.update { it.copy(content = event.content) }
            WorkspaceEditorEvent.Save -> save()
            WorkspaceEditorEvent.ConsumeMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            readText(item)
                .onSuccess { text ->
                    _uiState.update {
                        it.copy(isLoading = false, content = text, savedContent = text)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't open this file as text.")
                    }
                }
        }
    }

    private fun save() {
        val current = _uiState.value.content
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            saveText(item, current)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, savedContent = current, message = "Saved") }
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, message = "Save failed") }
                }
        }
    }

    companion object {
        fun factory(appContext: Context, id: String, name: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val repo = WorkspaceModule.repository(appContext)
                    val writeToken = WorkspaceModule.writeCapability()
                    val item = WorkspaceItem(
                        id = id,
                        name = name,
                        ref = StorageRef.workspace(id),
                        sizeBytes = 0L,
                        addedAt = 0L,
                    )
                    WorkspaceEditorViewModel(
                        readText = ReadWorkspaceTextUseCase(repo),
                        saveText = SaveWorkspaceTextUseCase(repo, writeToken),
                        item = item,
                    )
                }
            }
    }
}
