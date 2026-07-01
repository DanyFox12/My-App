package com.devexplorer.core.capability

import com.devexplorer.core.model.InstalledPackage
import com.devexplorer.core.model.PermissionUsage
import java.io.InputStream

/**
 * READ-ONLY access to installed packages via PackageManager. Holds
 * [ReadCapability] only — it lists and inspects packages, never installs,
 * uninstalls, or modifies them.
 */
interface PackagesRepository {

    /** List installed packages, optionally including system apps. */
    suspend fun listInstalled(includeSystem: Boolean): List<InstalledPackage>

    /** Reverse index: which apps request each permission. */
    suspend fun permissionUsage(includeSystem: Boolean): List<PermissionUsage>

    /**
     * Open a READ-ONLY stream to an installed package's base APK, so it can be
     * analyzed by the same read-only pipeline as a file-based APK.
     */
    suspend fun openApk(packageName: String): InputStream
}
