package com.devexplorer.core.usecase

import com.devexplorer.core.capability.ApkRepository
import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.DexPackageNode
import com.devexplorer.core.model.StorageRef

/**
 * Loads the per-package DEX tree for an APK, from either source behind one
 * [StorageRef] (a SAF document or an installed package) — the same seam as
 * [AnalyzeApkUseCase], kept separate because it walks whole DEX files and is
 * triggered on demand from the viewer, not during the initial analysis.
 * Read-only throughout. Success with a null tree means "no parseable DEX".
 */
class ReadDexPackagesUseCase(
    private val storage: StorageRepository,
    private val packages: PackagesRepository,
    private val apk: ApkRepository,
) {
    suspend operator fun invoke(source: StorageRef): Result<DexPackageNode?> = runCatching {
        val input = when (source.kind) {
            StorageRef.Kind.InstalledPackage -> packages.openApk(source.raw)
            else -> storage.openInputStream(source)
        }
        input.use { apk.readDexPackages(it) }
    }
}
