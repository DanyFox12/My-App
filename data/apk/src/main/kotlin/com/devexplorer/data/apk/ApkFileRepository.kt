package com.devexplorer.data.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.devexplorer.core.capability.ApkRepository
import com.devexplorer.core.capability.ReadCapability
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.ArchiveEntry
import com.devexplorer.core.model.CompressionMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * READ-ONLY APK/ZIP analyzer.
 *
 * Two sources of truth, both read-only:
 *  1. **The ZIP central directory** — enumerated with [ZipFile] to list every
 *     entry with its stored/compressed sizes and compression method. This is the
 *     archive's own table of contents.
 *  2. **The platform package parser** — [PackageManager.getPackageArchiveInfo]
 *     reads the (binary) AndroidManifest.xml and resource table to give package
 *     name, versions, SDK levels and requested permissions.
 *
 * Because `getPackageArchiveInfo` requires a real file *path* (it can't read a
 * content Uri), we stage the incoming stream into our own cache as a temp file,
 * analyze it, and delete it in a `finally`. That temp copy is a private
 * implementation detail — the user's source APK is only ever read.
 */
class ApkFileRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ApkRepository, ReadCapability {

    private val appContext = context.applicationContext

    override suspend fun analyze(input: InputStream): ApkSummary = withContext(io) {
        val temp = File.createTempFile("analyze_", ".apk", appContext.cacheDir)
        try {
            FileOutputStream(temp).use { output -> input.copyTo(output) }
            val entries = readEntries(temp)
            val packageInfo = readPackageInfo(temp)
            buildSummary(temp, entries, packageInfo)
        } finally {
            temp.delete()
        }
    }

    /** Enumerate the ZIP central directory. */
    private fun readEntries(file: File): List<ArchiveEntry> =
        ZipFile(file).use { zip ->
            val result = ArrayList<ArchiveEntry>()
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                result += ArchiveEntry(
                    name = entry.name,
                    sizeBytes = entry.size.coerceAtLeast(0),
                    compressedSizeBytes = entry.compressedSize.coerceAtLeast(0),
                    isDirectory = entry.isDirectory,
                    method = entry.method.toCompressionMethod(),
                )
            }
            result
        }

    /** Ask the platform to parse the archive as a package (null if it isn't one). */
    private fun readPackageInfo(file: File): PackageInfo? {
        val pm = appContext.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.path, PackageManager.GET_PERMISSIONS)
        // loadLabel needs the app's source paths pointed at the archive.
        info?.applicationInfo?.apply {
            sourceDir = file.path
            publicSourceDir = file.path
        }
        return info
    }

    private fun buildSummary(
        file: File,
        entries: List<ArchiveEntry>,
        info: PackageInfo?,
    ): ApkSummary {
        val appInfo = info?.applicationInfo
        val label = runCatching { appInfo?.loadLabel(appContext.packageManager)?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != info?.packageName }

        val dexCount = entries.count { DEX_REGEX.matches(it.name) }
        val signatureFiles = entries
            .filter { !it.isDirectory && it.name.startsWith("META-INF/") && it.name.isSignatureFile() }
            .map { it.name }

        return ApkSummary(
            packageName = info?.packageName,
            appLabel = label,
            versionName = info?.versionName,
            versionCode = info?.let { it.longVersionCodeCompat() },
            minSdk = appInfo?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.minSdkVersion else null },
            targetSdk = appInfo?.targetSdkVersion,
            compileSdk = appInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.compileSdkVersion else null
            },
            permissions = info?.requestedPermissions?.toList().orEmpty(),
            entries = entries,
            dexCount = dexCount,
            hasResourcesArsc = entries.any { it.name == "resources.arsc" },
            hasBinaryManifest = entries.any { it.name == "AndroidManifest.xml" },
            signatureFiles = signatureFiles,
            totalUncompressedBytes = entries.sumOf { it.sizeBytes },
            totalCompressedBytes = entries.sumOf { it.compressedSizeBytes },
        )
    }

    private fun Int.toCompressionMethod(): CompressionMethod = when (this) {
        ZipEntry.STORED -> CompressionMethod.Stored
        ZipEntry.DEFLATED -> CompressionMethod.Deflated
        else -> CompressionMethod.Other
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else versionCode.toLong()

    private fun String.isSignatureFile(): Boolean {
        val upper = uppercase()
        return upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC") ||
            upper.endsWith("MANIFEST.MF") || upper.endsWith(".SF")
    }

    private companion object {
        val DEX_REGEX = Regex("""classes\d*\.dex""")
    }
}
