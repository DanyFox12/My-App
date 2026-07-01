package com.devexplorer.core.usecase

import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.StorageRef

/** The root location produced when the user picks a folder in the SAF picker. */
data class PickedRoot(
    val ref: StorageRef,
    val displayName: String,
)

/**
 * Handles a freshly picked SAF tree Uri: persists the long-term READ grant and
 * resolves the root [StorageRef] + display name to navigate from.
 *
 * Returns a [Result] so the caller (ViewModel) can render a friendly error
 * instead of crashing if the grant/resolution fails.
 */
class OpenDocumentTreeUseCase(
    private val storage: StorageRepository,
) {
    suspend operator fun invoke(treeUri: String): Result<PickedRoot> = runCatching {
        storage.persistReadPermission(treeUri)
        val root = storage.treeRootRef(treeUri)
        PickedRoot(ref = root, displayName = storage.displayName(root))
    }
}
