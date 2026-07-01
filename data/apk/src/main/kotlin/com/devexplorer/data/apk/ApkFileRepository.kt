package com.devexplorer.data.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.devexplorer.core.capability.ApkRepository
import com.devexplorer.core.capability.ReadCapability
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.ArchiveEntry
import com.devexplorer.core.model.BinaryXml
import com.devexplorer.core.model.CompressionMethod
import com.devexplorer.core.model.Dex
import com.devexplorer.core.model.DexStats
import com.devexplorer.core.model.XmlNode
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
            val manifest = readManifest(temp)
            val dexStats = readDexStats(temp, entries)
            buildSummary(temp, entries, packageInfo, manifest, dexStats)
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

    /** Read AndroidManifest.xml bytes and decode them with our binary-XML decoder. */
    private fun readManifest(file: File): XmlNode? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            BinaryXml.decode(bytes)
        }
    }.getOrNull()

    /**
     * Parse each `classes*.dex` header for its method/class counts. Only the
     * fixed 0x70-byte header is read per DEX (not the whole file), so this stays
     * cheap even for large multidex apps. Returns null when there are no DEX
     * files or none parse (fail-soft, matching the manifest decoder).
     */
    private fun readDexStats(file: File, entries: List<ArchiveEntry>): DexStats? {
        val dexEntries = entries.filter { !it.isDirectory && DEX_REGEX.matches(it.name) }
        if (dexEntries.isEmpty()) return null
        val files = ZipFile(file).use { zip ->
            dexEntries.mapNotNull { archiveEntry ->
                val zipEntry = zip.getEntry(archiveEntry.name) ?: return@mapNotNull null
                val header = zip.getInputStream(zipEntry).use { it.readAtMost(DEX_HEADER_BYTES) }
                Dex.parseHeader(archiveEntry.name, header, archiveEntry.sizeBytes)
            }
        }.sortedBy { it.name }
        return if (files.isEmpty()) null else DexStats(files)
    }

    /** Read up to [n] bytes, tolerating short reads from the deflate stream. */
    private fun InputStream.readAtMost(n: Int): ByteArray {
        val buffer = ByteArray(n)
        var read = 0
        while (read < n) {
            val count = read(buffer, read, n - read)
            if (count < 0) break
            read += count
        }
        return if (read == n) buffer else buffer.copyOf(read)
    }

    /** Ask the platform to parse the archive as a package (null if it isn't one). */
    private fun readPackageInfo(file: File): PackageInfo? {
        val pm = appContext.packageManager
        // Request permissions AND the signing certificates in one parse.
        val signingFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.path, PackageManager.GET_PERMISSIONS or signingFlag)
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
        manifest: XmlNode?,
        dexStats: DexStats?,
    ): ApkSummary {
        val appInfo = info?.applicationInfo
        val label = runCatching { appInfo?.loadLabel(appContext.packageManager)?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != info?.packageName }

        val dexCount = entries.count { DEX_REGEX.matches(it.name) }
        val signatureFiles = entries
            .filter { !it.isDirectory && it.name.startsWith("META-INF/") && it.name.isSignatureFile() }
            .map { it.name }
        val signingInfo = info?.let { SignatureExtractor.extract(it, signatureFiles.isNotEmpty()) }

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
            signingInfo = signingInfo,
            totalUncompressedBytes = entries.sumOf { it.sizeBytes },
            totalCompressedBytes = entries.sumOf { it.compressedSizeBytes },
            manifest = manifest,
            dexStats = dexStats,
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

        /** Bytes of a DEX file header — all the count fields live within it. */
        const val DEX_HEADER_BYTES = 0x70
    }
}
