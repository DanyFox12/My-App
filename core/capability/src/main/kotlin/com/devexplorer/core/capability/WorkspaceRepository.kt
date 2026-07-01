package com.devexplorer.core.capability

import com.devexplorer.core.model.WorkspaceItem
import java.io.InputStream

/**
 * Read/write access to the Workspace sandbox — the ONLY writable surface in the
 * app (app-private `filesDir/workspace`).
 *
 * Every mutating method demands a [WriteCapability] token. Because the sole
 * implementation of [WriteCapability] lives in :data:workspace and nothing else
 * can construct one, no analysis/browsing code path can reach these writes — the
 * "APK analysis is read-only" guarantee is enforced by the type system, not by
 * convention. The implementation additionally refuses any path that escapes the
 * sandbox root (directory-traversal defense). See docs/ARCHITECTURE.md §10.
 */
interface WorkspaceRepository {

    /** List the sandbox contents (newest first). Read-only; no token required. */
    suspend fun list(): List<WorkspaceItem>

    /** Read a sandbox item's contents as UTF-8 text. Read-only; no token required. */
    suspend fun readText(item: WorkspaceItem): String

    /**
     * Overwrite an existing sandbox item's contents with [text] (UTF-8). This is
     * the only in-place edit path, and — like every mutator here — it demands a
     * [WriteCapability], so nothing outside :data:workspace can reach it.
     */
    suspend fun writeText(
        item: WorkspaceItem,
        text: String,
        capability: WriteCapability,
    ): WorkspaceItem

    /**
     * Copy an incoming read-only [input] stream into the sandbox under a safe,
     * unique name derived from [displayName]. Returns the created item.
     */
    suspend fun importStream(
        displayName: String,
        input: InputStream,
        capability: WriteCapability,
    ): WorkspaceItem

    /** Rename an existing sandbox item; name is sanitized and kept unique. */
    suspend fun rename(
        item: WorkspaceItem,
        newName: String,
        capability: WriteCapability,
    ): WorkspaceItem

    /** Delete a sandbox item. */
    suspend fun delete(item: WorkspaceItem, capability: WriteCapability)
}
