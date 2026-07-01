package com.devexplorer.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class ApkDiffReportTest {

    private fun entry(name: String, size: Long) =
        ArchiveEntry(name, size, size, isDirectory = false, method = CompressionMethod.Deflated)

    private fun summary(
        versionName: String,
        permissions: List<String>,
        entries: List<ArchiveEntry>,
        signingInfo: SigningInfo? = null,
    ) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = versionName,
        versionCode = 1L,
        minSdk = 24,
        targetSdk = 35,
        compileSdk = 35,
        permissions = permissions,
        entries = entries,
        dexCount = 1,
        hasResourcesArsc = true,
        hasBinaryManifest = true,
        signatureFiles = emptyList(),
        signingInfo = signingInfo,
        totalUncompressedBytes = entries.sumOf { it.sizeBytes },
        totalCompressedBytes = entries.sumOf { it.compressedSizeBytes },
    )

    @Test
    fun renders_changes_with_signed_deltas() {
        val old = summary("1.0", listOf("android.permission.CAMERA"), listOf(entry("classes.dex", 100)))
        val new = summary("1.1", listOf("android.permission.RECORD_AUDIO"), listOf(entry("classes.dex", 250)))

        val report = buildApkDiffReport(diffApks(old, new), oldName = "old.apk", newName = "new.apk")

        assertTrue(report.contains("old: old.apk"))
        assertTrue(report.contains("new: new.apk"))
        assertTrue(report.contains("~ Version name: 1.0 -> 1.1"))
        assertTrue(report.contains("(+150)"))
        assertTrue(report.contains("+ android.permission.RECORD_AUDIO"))
        assertTrue(report.contains("- android.permission.CAMERA"))
    }

    @Test
    fun states_when_identical() {
        val s = summary("1.0", listOf("android.permission.INTERNET"), listOf(entry("classes.dex", 100)))
        val report = buildApkDiffReport(diffApks(s, s))
        assertTrue(report.contains("No meaningful differences found."))
    }

    @Test
    fun warns_on_different_signer() {
        fun cert(sha: String) = CertificateInfo("CN=X", "CN=X", "01", 0L, 0L, sha, "00", "SHA256withRSA", "RSA")
        val old = summary(
            "1.0", emptyList(), listOf(entry("classes.dex", 100)),
            signingInfo = SigningInfo(listOf(cert("AA")), hasMultipleSigners = false, schemeV1 = true),
        )
        val new = summary(
            "1.0", emptyList(), listOf(entry("classes.dex", 100)),
            signingInfo = SigningInfo(listOf(cert("BB")), hasMultipleSigners = false, schemeV1 = true),
        )
        val report = buildApkDiffReport(diffApks(old, new))
        assertTrue(report.contains("DIFFERENT certificate set"))
    }
}
