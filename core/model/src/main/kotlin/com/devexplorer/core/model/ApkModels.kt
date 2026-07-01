package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * The result of a READ-ONLY analysis of an APK (or any ZIP). Immutable snapshot;
 * producing it never modifies the source archive.
 *
 * Package-level fields ([packageName], versions, SDKs, [permissions]) come from
 * the platform's own package parser; [entries] and the archive stats come from
 * reading the ZIP central directory directly.
 */
@Serializable
data class ApkSummary(
    val packageName: String?,
    val appLabel: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val compileSdk: Int?,
    val permissions: List<String>,
    val entries: List<ArchiveEntry>,
    val dexCount: Int,
    val hasResourcesArsc: Boolean,
    val hasBinaryManifest: Boolean,
    val signatureFiles: List<String>,
    val signingInfo: SigningInfo?,
    val totalUncompressedBytes: Long,
    val totalCompressedBytes: Long,
) {
    /** True when the platform parser recognized this as an installable package. */
    val isValidPackage: Boolean get() = packageName != null

    val entryCount: Int get() = entries.count { !it.isDirectory }
}

/** One entry in the ZIP central directory. */
@Serializable
data class ArchiveEntry(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean,
    val method: CompressionMethod,
) {
    /** Ratio saved by compression, 0f..1f; 0 for stored/empty entries. */
    val compressionRatio: Float
        get() = if (sizeBytes > 0) 1f - (compressedSizeBytes.toFloat() / sizeBytes) else 0f
}

enum class CompressionMethod { Stored, Deflated, Other }
