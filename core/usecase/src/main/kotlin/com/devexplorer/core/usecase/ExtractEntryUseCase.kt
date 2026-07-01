package com.devexplorer.core.usecase

import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.capability.WorkspaceRepository
import com.devexplorer.core.capability.WriteCapability
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.WorkspaceItem
import java.util.zip.ZipInputStream

/**
 * Extracts a single entry from an APK/ZIP INTO the Workspace sandbox.
 *
 * This is the "read meets write" seam, just like [CopyIntoWorkspaceUseCase]: the
 * source archive is opened **read-only** (a SAF document or an installed
 * package's base APK), its central directory is streamed until the requested
 * entry is found, and only that entry's bytes are written — through the
 * [WorkspaceRepository], the sole writer — under a safe leaf name. The source is
 * never modified. The [WriteCapability] is injected at construction, so this
 * use-case can only be *built* by code holding a token (minted only in
 * :data:workspace).
 */
class ExtractEntryUseCase(
    private val storage: StorageRepository,
    private val packages: PackagesRepository,
    private val workspace: WorkspaceRepository,
    private val capability: WriteCapability,
) {
    suspend operator fun invoke(source: StorageRef, entryName: String): Result<WorkspaceItem> =
        runCatching {
            require(entryName.isNotBlank()) { "An entry name is required." }
            val input = when (source.kind) {
                StorageRef.Kind.InstalledPackage -> packages.openApk(source.raw)
                else -> storage.openInputStream(source)
            }
            input.use { raw ->
                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name == entryName) {
                            // ZipInputStream reports EOF at the end of the current entry,
                            // so handing it straight to importStream copies just this entry.
                            val leafName = entryName.substringAfterLast('/').ifBlank { "entry" }
                            return@runCatching workspace.importStream(leafName, zip, capability)
                        }
                        entry = zip.nextEntry
                    }
                    throw NoSuchElementException("Entry not found in archive: $entryName")
                }
            }
        }
}
