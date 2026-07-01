package com.devexplorer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkReportTest {

    private fun entry(name: String, size: Long) =
        ArchiveEntry(name, size, size, isDirectory = false, method = CompressionMethod.Deflated)

    private fun summary(
        permissions: List<String> = emptyList(),
        signingInfo: SigningInfo? = null,
        entries: List<ArchiveEntry> = listOf(entry("classes.dex", 100)),
    ) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = "1.2.3",
        versionCode = 42L,
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
        totalUncompressedBytes = 100L,
        totalCompressedBytes = 60L,
    )

    @Test
    fun includes_identity_and_archive_facts() {
        val report = buildApkReport(summary())

        assertTrue(report.contains("Package: com.example.app"))
        assertTrue(report.contains("Version name: 1.2.3"))
        assertTrue(report.contains("Target SDK: 35"))
        assertTrue(report.contains("resources.arsc: present"))
        // Composition is derived from the entries, not stored on the summary.
        assertTrue(report.contains("Composition (uncompressed)"))
        assertTrue(report.contains("DEX code: 100 bytes"))
    }

    @Test
    fun lists_permissions_and_falls_back_to_none() {
        val withPerms = buildApkReport(summary(permissions = listOf("android.permission.CAMERA")))
        assertTrue(withPerms.contains("Permissions (1)"))
        assertTrue(withPerms.contains("android.permission.CAMERA"))

        val withoutPerms = buildApkReport(summary(permissions = emptyList()))
        assertTrue(withoutPerms.contains("Permissions (0)"))
        assertTrue(withoutPerms.contains("(none)"))
    }

    @Test
    fun renders_signing_fingerprints_when_present() {
        val cert = CertificateInfo(
            subject = "CN=Example",
            issuer = "CN=Example",
            serialNumber = "01",
            notBefore = 0L,
            notAfter = 0L,
            sha256 = "AA:BB",
            sha1 = "CC:DD",
            signatureAlgorithm = "SHA256withRSA",
            publicKeyAlgorithm = "RSA",
        )
        val report = buildApkReport(
            summary(signingInfo = SigningInfo(listOf(cert), hasMultipleSigners = false, schemeV1 = true)),
        )

        assertTrue(report.contains("v1 (JAR) scheme: yes"))
        assertTrue(report.contains("Subject: CN=Example"))
        assertTrue(report.contains("SHA-256: AA:BB"))
        assertTrue(report.contains("SHA-1: CC:DD"))
    }

    @Test
    fun states_when_no_signing_certificate() {
        val report = buildApkReport(summary(signingInfo = null))
        assertTrue(report.contains("No signing certificate found."))
        assertFalse(report.contains("SHA-256:"))
    }
}
