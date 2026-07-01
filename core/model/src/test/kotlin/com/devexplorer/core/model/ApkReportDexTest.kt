package com.devexplorer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkReportDexTest {

    private fun entry(name: String, size: Long) =
        ArchiveEntry(name, size, size, isDirectory = false, method = CompressionMethod.Deflated)

    private fun summary(dexStats: DexStats?) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 24,
        targetSdk = 35,
        compileSdk = 35,
        permissions = emptyList(),
        entries = listOf(entry("classes.dex", 100)),
        dexCount = 1,
        hasResourcesArsc = true,
        hasBinaryManifest = true,
        signatureFiles = emptyList(),
        signingInfo = null,
        totalUncompressedBytes = 100L,
        totalCompressedBytes = 60L,
        dexStats = dexStats,
    )

    private fun dexFile(name: String, methods: Int, classes: Int) = DexFileStats(
        name = name, stringIds = 0, typeIds = 0, protoIds = 0, fieldIds = 0,
        methodIds = methods, classDefs = classes, sizeBytes = 0L,
    )

    @Test
    fun includes_dex_section_and_limit_warning_when_present() {
        val report = buildApkReport(
            summary(DexStats(listOf(dexFile("classes.dex", methods = 61_000, classes = 900)))),
        )
        assertTrue(report.contains("DEX (methods count toward the 65,536-per-file limit)"))
        assertTrue(report.contains("Methods: 61000"))
        assertTrue(report.contains("classes.dex: 61000 methods, 900 classes"))
        assertTrue(report.contains("[near 64K limit]"))
    }

    @Test
    fun omits_dex_section_when_absent() {
        val report = buildApkReport(summary(dexStats = null))
        assertFalse(report.contains("DEX (methods count"))
    }
}
