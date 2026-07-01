package com.devexplorer.core.usecase

import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.model.PermissionUsage

/**
 * Reverse permission lookup: "which apps request each permission?". Wrapped in
 * [Result] so a PackageManager hiccup surfaces as UI state, not a crash.
 */
class GetPermissionUsageUseCase(
    private val packages: PackagesRepository,
) {
    suspend operator fun invoke(includeSystem: Boolean): Result<List<PermissionUsage>> =
        runCatching { packages.permissionUsage(includeSystem) }
}
