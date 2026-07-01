package com.devexplorer.core.usecase

import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.capability.WorkspaceRepository
import com.devexplorer.core.capability.WriteCapability
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.WorkspaceItem

/** Lists the sandbox contents. Read-only, so no capability is required. */
class ListWorkspaceUseCase(
    private val workspace: WorkspaceRepository,
) {
    suspend operator fun invoke(): Result<List<WorkspaceItem>> = runCatching {
        workspace.list()
    }
}

/**
 * Copies a System-zone file INTO the Workspace sandbox.
 *
 * This is the one place read meets write: it reads the source through the
 * [StorageRepository] (read-only, via SAF) and writes the bytes through the
 * [WorkspaceRepository] (the only writer). The [WriteCapability] is injected at
 * construction, so this use-case can only be *built* by code that holds a token
 * — which only :data:workspace can mint.
 */
class CopyIntoWorkspaceUseCase(
    private val storage: StorageRepository,
    private val workspace: WorkspaceRepository,
    private val capability: WriteCapability,
) {
    suspend operator fun invoke(source: FileNode): Result<WorkspaceItem> = runCatching {
        require(!source.isDirectory) { "Only files can be copied into the Workspace." }
        storage.openInputStream(source.ref).use { input ->
            workspace.importStream(source.name, input, capability)
        }
    }
}

/** Renames a sandbox item. Requires a write token. */
class RenameWorkspaceItemUseCase(
    private val workspace: WorkspaceRepository,
    private val capability: WriteCapability,
) {
    suspend operator fun invoke(item: WorkspaceItem, newName: String): Result<WorkspaceItem> =
        runCatching {
            require(newName.isNotBlank()) { "Name cannot be empty." }
            workspace.rename(item, newName.trim(), capability)
        }
}

/** Deletes a sandbox item. Requires a write token. */
class DeleteWorkspaceItemUseCase(
    private val workspace: WorkspaceRepository,
    private val capability: WriteCapability,
) {
    suspend operator fun invoke(item: WorkspaceItem): Result<Unit> = runCatching {
        workspace.delete(item, capability)
    }
}

/** Reads a sandbox item's text for the editor. Read-only; no token required. */
class ReadWorkspaceTextUseCase(
    private val workspace: WorkspaceRepository,
) {
    suspend operator fun invoke(item: WorkspaceItem): Result<String> = runCatching {
        workspace.readText(item)
    }
}

/**
 * Saves edited text back to a sandbox item — the app's only in-place *edit*.
 * Reads happen anywhere; this write is gated by the injected [WriteCapability],
 * so only :data:workspace-blessed code can construct it.
 */
class SaveWorkspaceTextUseCase(
    private val workspace: WorkspaceRepository,
    private val capability: WriteCapability,
) {
    suspend operator fun invoke(item: WorkspaceItem, text: String): Result<WorkspaceItem> =
        runCatching {
            workspace.writeText(item, text, capability)
        }
}
