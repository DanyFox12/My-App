package com.devexplorer.app.feature.explorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.RecentLocation
import com.devexplorer.core.usecase.CopyIntoWorkspaceUseCase
import com.devexplorer.core.usecase.ListDirectoryUseCase
import com.devexplorer.core.usecase.ObserveRecentLocationsUseCase
import com.devexplorer.core.usecase.OpenDocumentTreeUseCase
import com.devexplorer.core.usecase.RecordRecentLocationUseCase
import com.devexplorer.core.usecase.RemoveRecentLocationUseCase
import com.devexplorer.data.db.DbModule
import com.devexplorer.data.storage.SafStorageRepository
import com.devexplorer.data.workspace.WorkspaceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Explorer. Holds a [StateFlow] of [ExplorerUiState], reduces
 * [ExplorerEvent]s, and delegates all IO to use-cases (which run off the main
 * thread inside the repository). Folder navigation is modeled as a breadcrumb
 * stack inside the state, so "up" is just popping the stack.
 *
 * DI note: milestone 2 uses a hand-written [factory]. Milestone 3+ replaces it
 * with Hilt; the ViewModel itself already depends only on use-cases, so that
 * swap won't touch this class.
 */
class ExplorerViewModel(
    private val openDocumentTree: OpenDocumentTreeUseCase,
    private val listDirectory: ListDirectoryUseCase,
    private val copyIntoWorkspace: CopyIntoWorkspaceUseCase,
    private val observeRecents: ObserveRecentLocationsUseCase,
    private val recordRecent: RecordRecentLocationUseCase,
    private val removeRecent: RemoveRecentLocationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        // Reactive recents: Room re-emits whenever the history changes.
        observeRecents()
            .onEach { recents -> _uiState.update { it.copy(recents = recents) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ExplorerEvent) {
        when (event) {
            is ExplorerEvent.TreePicked -> onTreePicked(event.treeUri)
            is ExplorerEvent.OpenFolder -> onOpenFolder(event.node)
            is ExplorerEvent.OpenFile -> onOpenFile(event.node)
            is ExplorerEvent.CopyToWorkspace -> onCopyToWorkspace(event.node)
            is ExplorerEvent.OpenRecent -> onOpenRecent(event.recent)
            is ExplorerEvent.RemoveRecent -> viewModelScope.launch { removeRecent(event.recent.ref) }
            is ExplorerEvent.NavigateToCrumb -> onNavigateToCrumb(event.index)
            ExplorerEvent.NavigateUp -> onNavigateUp()
            ExplorerEvent.Retry -> loadCurrent()
            ExplorerEvent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
            ExplorerEvent.ConsumeMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun onCopyToWorkspace(node: FileNode) {
        if (node.isDirectory) return
        viewModelScope.launch {
            copyIntoWorkspace(node)
                .onSuccess { item ->
                    _uiState.update { it.copy(message = "Copied \"${item.name}\" to Workspace") }
                }
                .onFailure {
                    _uiState.update { it.copy(message = "Couldn't copy to Workspace") }
                }
        }
    }

    private fun onTreePicked(treeUri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            openDocumentTree(treeUri)
                .onSuccess { root ->
                    _uiState.update {
                        it.copy(
                            hasRoot = true,
                            breadcrumb = listOf(Crumb(root.ref, root.displayName)),
                        )
                    }
                    recordRecent(root.ref, root.displayName)
                    loadCurrent()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.toUserMessage())
                    }
                }
        }
    }

    private fun onOpenRecent(recent: RecentLocation) {
        _uiState.update {
            it.copy(
                hasRoot = true,
                breadcrumb = listOf(Crumb(recent.ref, recent.label)),
                errorMessage = null,
            )
        }
        // Bump its timestamp so reopening moves it to the top of the history.
        viewModelScope.launch { recordRecent(recent.ref, recent.label) }
        loadCurrent()
    }

    private fun onOpenFolder(node: FileNode) {
        if (!node.isDirectory) return
        _uiState.update { it.copy(breadcrumb = it.breadcrumb + Crumb(node.ref, node.name)) }
        loadCurrent()
    }

    private fun onOpenFile(node: FileNode) {
        // File opening (CodeViewer / ApkViewer) arrives in later milestones.
        // For now, tapping a file is a safe no-op.
    }

    private fun onNavigateUp() {
        val bc = _uiState.value.breadcrumb
        if (bc.size <= 1) return
        _uiState.update { it.copy(breadcrumb = bc.dropLast(1)) }
        loadCurrent()
    }

    private fun onNavigateToCrumb(index: Int) {
        val bc = _uiState.value.breadcrumb
        if (index !in bc.indices || index == bc.lastIndex) return
        _uiState.update { it.copy(breadcrumb = bc.subList(0, index + 1)) }
        loadCurrent()
    }

    private fun loadCurrent() {
        val current = _uiState.value.current ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            listDirectory(current.ref)
                .onSuccess { nodes ->
                    _uiState.update { it.copy(isLoading = false, entries = nodes) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, entries = emptyList(), errorMessage = e.toUserMessage())
                    }
                }
        }
    }

    companion object {
        /**
         * Manual dependency wiring for milestone 2. Builds the SAF repository from
         * the application context and threads it through the use-cases.
         */
        fun factory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val storage = SafStorageRepository(appContext)
                val workspace = WorkspaceModule.repository(appContext)
                // The write token is minted here (only :data:workspace can) and
                // handed to the copy use-case; read paths never receive one.
                val writeToken = WorkspaceModule.writeCapability()
                val recents = DbModule.recentLocationsRepository(appContext)
                ExplorerViewModel(
                    openDocumentTree = OpenDocumentTreeUseCase(storage),
                    listDirectory = ListDirectoryUseCase(storage),
                    copyIntoWorkspace = CopyIntoWorkspaceUseCase(storage, workspace, writeToken),
                    observeRecents = ObserveRecentLocationsUseCase(recents),
                    recordRecent = RecordRecentLocationUseCase(recents),
                    removeRecent = RemoveRecentLocationUseCase(recents),
                )
            }
        }
    }
}

/** Maps low-level failures to short, friendly, non-technical messages. */
private fun Throwable.toUserMessage(): String = when (this) {
    is SecurityException -> "Access to this location was denied. Try picking the folder again."
    else -> "Couldn't read this folder. Please try again."
}
