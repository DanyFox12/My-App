package com.devexplorer.core.usecase

import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.StorageRef

/**
 * Lists the children of a directory, sorted for display (directories first, then
 * case-insensitive by name). Wraps the repository call in a [Result] so IO or
 * permission failures surface as recoverable UI state, never crashes.
 */
class ListDirectoryUseCase(
    private val storage: StorageRepository,
) {
    suspend operator fun invoke(directory: StorageRef): Result<List<FileNode>> = runCatching {
        storage.listChildren(directory)
            .sortedWith(
                compareByDescending<FileNode> { it.isDirectory }
                    .thenBy { it.name.lowercase() },
            )
    }
}
