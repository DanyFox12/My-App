package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/** Which installed apps request a given permission (reverse lookup). */
@Serializable
data class PermissionUsage(
    val permission: String,
    val apps: List<InstalledPackage>,
) {
    val count: Int get() = apps.size

    /** The short, human-facing tail, e.g. "CAMERA" from "android.permission.CAMERA". */
    val shortName: String get() = permission.substringAfterLast('.')
}

/**
 * Inverts an apps→permissions mapping into permission→apps, sorted by how many
 * apps request each permission (then by name). Pure and deterministic, so it's
 * unit-tested; the Android layer just supplies the (app, permissions) pairs.
 */
fun buildPermissionIndex(
    appsWithPermissions: List<Pair<InstalledPackage, List<String>>>,
): List<PermissionUsage> {
    val byPermission = LinkedHashMap<String, MutableList<InstalledPackage>>()
    for ((app, permissions) in appsWithPermissions) {
        for (permission in permissions) {
            byPermission.getOrPut(permission) { mutableListOf() }.add(app)
        }
    }
    return byPermission
        .map { (permission, apps) -> PermissionUsage(permission, apps.sortedBy { it.label.lowercase() }) }
        .sortedWith(compareByDescending<PermissionUsage> { it.count }.thenBy { it.permission })
}
