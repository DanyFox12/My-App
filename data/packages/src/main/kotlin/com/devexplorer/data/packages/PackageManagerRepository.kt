package com.devexplorer.data.packages

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.capability.ReadCapability
import com.devexplorer.core.model.InstalledPackage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

/**
 * READ-ONLY package inspection via [PackageManager].
 *
 * `getInstalledPackages` returns a `PackageInfo` per app; the `applicationInfo`
 * carries the label, flags (system vs user), SDK levels and the base APK path.
 * We never call install/delete APIs — this class only reads. It holds
 * [ReadCapability] and, like every analysis module, cannot obtain a write token.
 */
class PackageManagerRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PackagesRepository, ReadCapability {

    private val appContext = context.applicationContext
    private val pm: PackageManager get() = appContext.packageManager

    override suspend fun listInstalled(includeSystem: Boolean): List<InstalledPackage> =
        withContext(io) {
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledPackages(0)
            installed.mapNotNull { info -> info.toInstalledPackage(includeSystem) }
                .sortedBy { it.label.lowercase() }
        }

    override suspend fun openApk(packageName: String): InputStream = withContext(io) {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        // sourceDir is the base APK path; opening it for READ only.
        FileInputStream(appInfo.sourceDir)
    }

    private fun PackageInfo.toInstalledPackage(includeSystem: Boolean): InstalledPackage? {
        val appInfo = applicationInfo ?: return null
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (isSystem && !includeSystem) return null

        return InstalledPackage(
            packageName = packageName,
            label = appInfo.loadLabel(pm).toString(),
            versionName = versionName,
            versionCode = longVersionCodeCompat(),
            isSystem = isSystem,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else null,
            targetSdk = appInfo.targetSdkVersion,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            apkPath = appInfo.sourceDir,
        )
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else versionCode.toLong()
}
