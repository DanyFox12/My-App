package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/** A folder the user opened before, so the Explorer can offer to reopen it. */
@Serializable
data class RecentLocation(
    val ref: StorageRef,
    val label: String,
    val lastOpenedAt: Long,
)
