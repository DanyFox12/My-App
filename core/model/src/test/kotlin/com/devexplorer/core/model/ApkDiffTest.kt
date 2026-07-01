package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkDiffTest {

    private fun entry(name: String, size: Long) =
        ArchiveEntry(name, size, size, isDirectory = false, method = CompressionMethod.Deflated)

    private fun cert(sha256: String) = CertificateInfo(
        subject = "CN=X",
        issuer = "CN=X",
        serialNumber = "01",
        notBefore = 0L,
        notAfter = 0L,
        sha256 = sha256,
        sha1 = "00",
        signatureAlgorithm = "SHA256withRSA",
        publicKeyAlgorithm = "RSA",
    )

    private fun summary(
        versionCode: Long = 1L,
        versionName: String = "1.0",
        permissions: List<String> = emptyList(),
        entries: List<ArchiveEntry> = listOf(entry("classes.dex", 100)),
        dexStats: DexStats? = null,
        signingInfo: SigningInfo? = null,
    ) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = versionName,
        versionCode = versionCode,
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
        dexStats = dexStats,
    )

    @Test
    fun identifies_version_change_and_permission_delta() {
        val old = summary(
            versionCode = 1L,
            versionName = "1.0",
            permissions = listOf("android.permission.CAMERA", "android.permission.INTERNET"),
        )
        val new = summary(
            versionCode = 2L,
            versionName = "1.1",
            permissions = listOf("android.permission.INTERNET", "android.permission.RECORD_AUDIO"),
        )

        val diff = diffApks(old, new)

        val versionCode = diff.identity.single { it.label == "Version code" }
        assertTrue(versionCode.changed)
        assertEquals("1", versionCode.oldValue)
        assertEquals("2", versionCode.newValue)

        assertEquals(listOf("android.permission.RECORD_AUDIO"), diff.permissionsAdded)
        assertEquals(listOf("android.permission.CAMERA"), diff.permissionsRemoved)
        assertEquals(1, diff.permissionsCommon) // INTERNET
        assertFalse(diff.isIdentical)
    }

    @Test
    fun computes_size_and_composition_deltas() {
        val old = summary(entries = listOf(entry("classes.dex", 100), entry("res/a.png", 50)))
        val new = summary(entries = listOf(entry("classes.dex", 400), entry("res/a.png", 50)))

        val diff = diffApks(old, new)

        assertEquals(300L, diff.sizes.uncompressedDelta)
        val dexSlice = diff.composition.single { it.part == ApkPart.Dex }
        assertEquals(300L, dexSlice.delta)
        // Sorted largest-magnitude-first: DEX (+300) before Resources (0).
        assertEquals(ApkPart.Dex, diff.composition.first().part)
    }

    @Test
    fun computes_dex_method_delta_from_dex_stats() {
        val old = summary(dexStats = DexStats(listOf(dexFile("classes.dex", methods = 1000, classes = 10))))
        val new = summary(
            dexStats = DexStats(
                listOf(
                    dexFile("classes.dex", methods = 1000, classes = 10),
                    dexFile("classes2.dex", methods = 500, classes = 5),
                ),
            ),
        )

        val diff = diffApks(old, new)
        assertEquals(500, diff.dex.methodsDelta)
        assertEquals(5, diff.dex.classesDelta)
        assertEquals(1, diff.dex.dexFilesDelta)
    }

    @Test
    fun compares_signing_certificate_sets() {
        val a = summary(signingInfo = SigningInfo(listOf(cert("AA")), hasMultipleSigners = false, schemeV1 = true))
        val b = summary(signingInfo = SigningInfo(listOf(cert("AA")), hasMultipleSigners = false, schemeV1 = true))
        val c = summary(signingInfo = SigningInfo(listOf(cert("BB")), hasMultipleSigners = false, schemeV1 = true))
        val unsigned = summary(signingInfo = null)

        assertEquals(SigningComparison.Same, diffApks(a, b).signing)
        assertEquals(SigningComparison.Different, diffApks(a, c).signing)
        assertEquals(SigningComparison.Unknown, diffApks(a, unsigned).signing)
    }

    @Test
    fun identical_apks_report_no_differences() {
        val s = summary(permissions = listOf("android.permission.INTERNET"))
        val diff = diffApks(s, s)
        assertTrue(diff.isIdentical)
        assertTrue(diff.permissionsAdded.isEmpty())
        assertEquals(0, diff.dex.methodsDelta)
    }

    private fun dexFile(name: String, methods: Int, classes: Int) = DexFileStats(
        name = name,
        stringIds = 0,
        typeIds = 0,
        protoIds = 0,
        fieldIds = 0,
        methodIds = methods,
        classDefs = classes,
        sizeBytes = 0L,
    )
}
