package com.devexplorer.core.usecase

import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.model.InstalledPackage

/**
 * Lists installed packages (optionally including system apps), sorted by the
 * repository. Wrapped in [Result] so a PackageManager hiccup surfaces as UI
 * state rather than a crash.
 */
class ListInstalledPackagesUseCase(
    private val packages: PackagesRepository,
) {
    suspend operator fun invoke(includeSystem: Boolean): Result<List<InstalledPackage>> =
        runCatching { packages.listInstalled(includeSystem) }
}
