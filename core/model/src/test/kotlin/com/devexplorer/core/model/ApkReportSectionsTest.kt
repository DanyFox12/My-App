package com.devexplorer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the report sections added with the native-libs and security features. */
class ApkReportSectionsTest {

    private fun summary(
        manifest: XmlNode? = null,
        nativeLibs: NativeLibs? = null,
        permissions: List<String> = emptyList(),
    ) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 24,
        targetSdk = 35,
        compileSdk = 35,
        permissions = permissions,
        entries = emptyList(),
        dexCount = 0,
        hasResourcesArsc = false,
        hasBinaryManifest = manifest != null,
        signatureFiles = emptyList(),
        signingInfo = null,
        totalUncompressedBytes = 0L,
        totalCompressedBytes = 0L,
        manifest = manifest,
        nativeLibs = nativeLibs,
    )

    @Test
    fun includes_native_libraries_with_16k_flag() {
        val libs = NativeLibs(
            files = listOf(
                NativeLibFile("lib/arm64-v8a/liba.so", 100L, ElfInfo(true, "ARM64", 4_096L)),
            ),
        )
        val report = buildApkReport(summary(nativeLibs = libs))
        assertTrue(report.contains("Native libraries"))
        assertTrue(report.contains("arm64-v8a: 1 files, 100 bytes"))
        assertTrue(report.contains("[NOT 16 KB ready]"))
    }

    @Test
    fun includes_security_findings_with_evidence() {
        val manifest = XmlNode(
            "manifest",
            emptyList(),
            listOf(
                XmlNode(
                    "application",
                    listOf(XmlAttribute("android:debuggable", "true")),
                    emptyList(),
                ),
            ),
        )
        val report = buildApkReport(
            summary(manifest = manifest, permissions = listOf("android.permission.CAMERA")),
        )
        assertTrue(report.contains("Security audit"))
        assertTrue(report.contains("[warning] debuggable build"))
        assertTrue(report.contains("[info] sensitive permissions requested"))
        assertTrue(report.contains("- android.permission.CAMERA"))
    }

    @Test
    fun omits_sections_when_there_is_nothing_to_say() {
        val manifest = XmlNode("manifest", emptyList(), listOf(XmlNode("application", emptyList(), emptyList())))
        val report = buildApkReport(summary(manifest = manifest))
        assertFalse(report.contains("Native libraries"))
        assertFalse(report.contains("Security audit"))
    }
}
