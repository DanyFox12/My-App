package com.devexplorer.core.capability

import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.StorageRef
import java.io.InputStream

/**
 * Read-only access to browsable storage (the System zone).
 *
 * The implementation ([:data:storage]) is backed by the Storage Access
 * Framework, so the OS itself enforces "only files the user granted". This
 * interface exposes no write operation at all — writing lives behind a separate
 * repository that requires a [WriteCapability] (milestone 3).
 */
interface StorageRepository {

    /**
     * Turn a freshly picked SAF *tree* Uri (from the folder picker) into the
     * root [StorageRef] the rest of the app navigates from.
     */
    fun treeRootRef(treeUri: String): StorageRef

    /**
     * Persist long-term READ access to a picked tree so the grant survives app
     * restarts (via `takePersistableUriPermission`). Read-only by design.
     */
    suspend fun persistReadPermission(treeUri: String)

    /**
     * List the immediate children of a directory [parent]. Throws on IO / access
     * errors; callers wrap this in a use-case that returns a [Result].
     */
    suspend fun listChildren(parent: StorageRef): List<FileNode>

    /** Human-readable display name for a location (for breadcrumbs/titles). */
    suspend fun displayName(ref: StorageRef): String

    /**
     * Open a READ-ONLY stream to a document. The caller owns the stream and must
     * close it. Used to copy a source file into the Workspace sandbox — reading
     * only; the source is never modified.
     */
    suspend fun openInputStream(ref: StorageRef): InputStream
}
