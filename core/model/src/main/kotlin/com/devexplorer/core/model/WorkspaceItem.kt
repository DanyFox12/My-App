package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * An item living in the Workspace sandbox — the only writable zone in the app.
 *
 * [id] is the item's path *relative to* the sandbox root; it's stable enough to
 * address the item for rename/delete and never encodes anything outside the
 * sandbox. All fields are a snapshot; the data layer is the source of truth.
 */
@Serializable
data class WorkspaceItem(
    val id: String,
    val name: String,
    val ref: StorageRef,
    val sizeBytes: Long,
    val addedAt: Long,
    /** Where it came from (e.g. original file name). Persisted from milestone 9. */
    val sourceDescription: String? = null,
)
