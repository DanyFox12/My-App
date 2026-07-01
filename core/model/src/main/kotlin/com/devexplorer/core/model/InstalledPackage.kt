package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * A snapshot of one installed package, as reported by PackageManager. Read-only;
 * we never modify installed apps.
 */
@Serializable
data class InstalledPackage(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val minSdk: Int?,
    val targetSdk: Int?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    /** Path to the base APK on disk (applicationInfo.sourceDir), if known. */
    val apkPath: String?,
)
