package com.devexplorer.app.feature.workspace

import com.devexplorer.core.model.WorkspaceItem

/** Immutable UI state for the Workspace (the writable sandbox). */
data class WorkspaceUiState(
    val isLoading: Boolean = false,
    val items: List<WorkspaceItem> = emptyList(),
    val errorMessage: String? = null,
    val message: String? = null,
    /** Non-null while the rename dialog is open for this item. */
    val renameTarget: WorkspaceItem? = null,
    /** Non-null while the delete-confirm dialog is open for this item. */
    val deleteTarget: WorkspaceItem? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && items.isEmpty()
}

sealed interface WorkspaceEvent {
    data object Refresh : WorkspaceEvent
    data class RequestRename(val item: WorkspaceItem) : WorkspaceEvent
    data class ConfirmRename(val item: WorkspaceItem, val newName: String) : WorkspaceEvent
    data class RequestDelete(val item: WorkspaceItem) : WorkspaceEvent
    data class ConfirmDelete(val item: WorkspaceItem) : WorkspaceEvent
    data object DismissDialog : WorkspaceEvent
    data object ConsumeMessage : WorkspaceEvent
}
