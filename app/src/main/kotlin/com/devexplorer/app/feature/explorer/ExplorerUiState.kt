package com.devexplorer.app.feature.explorer

import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.RecentLocation
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.Zone

/** One level in the folder path — used both for the breadcrumb and back nav. */
data class Crumb(
    val ref: StorageRef,
    val name: String,
)

/**
 * Immutable UI state for the Explorer. The screen is a pure function of this;
 * all logic lives in [ExplorerViewModel]. (See docs/ARCHITECTURE.md §3.)
 */
data class ExplorerUiState(
    val zone: Zone = Zone.System,
    val hasRoot: Boolean = false,
    val isLoading: Boolean = false,
    val breadcrumb: List<Crumb> = emptyList(),
    val entries: List<FileNode> = emptyList(),
    val errorMessage: String? = null,
    /** Transient one-shot text for a snackbar (e.g. "Copied to Workspace"). */
    val message: String? = null,
    /** Recently opened folders, offered for one-tap reopen when no root is set. */
    val recents: List<RecentLocation> = emptyList(),
) {
    /** The folder currently being shown, or null before a root is picked. */
    val current: Crumb? get() = breadcrumb.lastOrNull()

    /** Whether an "up" navigation is possible (we're below the picked root). */
    val canNavigateUp: Boolean get() = breadcrumb.size > 1

    /** Show the empty-listing state only when a folder is loaded but has no items. */
    val isEmptyFolder: Boolean
        get() = hasRoot && !isLoading && errorMessage == null && entries.isEmpty()
}

/** Events the screen emits; the ViewModel reduces them into new state. */
sealed interface ExplorerEvent {
    data class TreePicked(val treeUri: String) : ExplorerEvent
    data class OpenFolder(val node: FileNode) : ExplorerEvent
    data class OpenFile(val node: FileNode) : ExplorerEvent
    data class CopyToWorkspace(val node: FileNode) : ExplorerEvent
    data class OpenRecent(val recent: RecentLocation) : ExplorerEvent
    data class RemoveRecent(val recent: RecentLocation) : ExplorerEvent
    data class NavigateToCrumb(val index: Int) : ExplorerEvent
    data object NavigateUp : ExplorerEvent
    data object Retry : ExplorerEvent
    data object DismissError : ExplorerEvent
    data object ConsumeMessage : ExplorerEvent
}
