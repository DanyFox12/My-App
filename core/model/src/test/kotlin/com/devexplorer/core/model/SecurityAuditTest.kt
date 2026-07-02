package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditTest {

    private fun node(name: String, attrs: List<Pair<String, String>> = emptyList(), children: List<XmlNode> = emptyList()) =
        XmlNode(name, attrs.map { XmlAttribute(it.first, it.second) }, children)

    private fun summary(
        manifest: XmlNode?,
        permissions: List<String> = emptyList(),
        targetSdk: Int? = 35,
    ) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 24,
        targetSdk = targetSdk,
        compileSdk = targetSdk,
        permissions = permissions,
        entries = emptyList(),
        dexCount = 1,
        hasResourcesArsc = true,
        hasBinaryManifest = manifest != null,
        signatureFiles = emptyList(),
        signingInfo = null,
        totalUncompressedBytes = 0L,
        totalCompressedBytes = 0L,
        manifest = manifest,
    )

    private fun List<SecurityFinding>.find(check: SecurityCheck) = firstOrNull { it.check == check }

    @Test
    fun flags_debuggable_and_cleartext_as_warnings() {
        val manifest = node(
            "manifest",
            children = listOf(
                node(
                    "application",
                    attrs = listOf("android:debuggable" to "true", "android:usesCleartextTraffic" to "true"),
                ),
            ),
        )
        val findings = securityAudit(summary(manifest))
        assertEquals(SecuritySeverity.Warning, findings.find(SecurityCheck.Debuggable)!!.severity)
        assertEquals(SecuritySeverity.Warning, findings.find(SecurityCheck.CleartextTraffic)!!.severity)
        // Warnings sort before quieter findings.
        assertEquals(SecuritySeverity.Warning, findings.first().severity)
    }

    @Test
    fun collects_exported_unguarded_components_but_not_guarded_ones() {
        val manifest = node(
            "manifest",
            children = listOf(
                node(
                    "application",
                    children = listOf(
                        node("activity", attrs = listOf("android:name" to ".Open", "android:exported" to "true")),
                        node(
                            "service",
                            attrs = listOf(
                                "android:name" to ".Guarded",
                                "android:exported" to "true",
                                "android:permission" to "com.example.PERM",
                            ),
                        ),
                        node("receiver", attrs = listOf("android:name" to ".Internal", "android:exported" to "false")),
                        node("provider", attrs = listOf("android:name" to ".Files", "android:exported" to "true")),
                    ),
                ),
            ),
        )
        val findings = securityAudit(summary(manifest))
        assertEquals(listOf(".Open"), findings.find(SecurityCheck.ExportedComponents)!!.items)
        assertEquals(listOf(".Files"), findings.find(SecurityCheck.ExportedProviders)!!.items)
        assertEquals(SecuritySeverity.Warning, findings.find(SecurityCheck.ExportedProviders)!!.severity)
    }

    @Test
    fun lists_dangerous_permissions_only() {
        val findings = securityAudit(
            summary(
                node("manifest", children = listOf(node("application"))),
                permissions = listOf(
                    "android.permission.CAMERA",
                    "android.permission.INTERNET",
                    "android.permission.READ_SMS",
                ),
            ),
        )
        assertEquals(
            listOf("android.permission.CAMERA", "android.permission.READ_SMS"),
            findings.find(SecurityCheck.DangerousPermissions)!!.items,
        )
    }

    @Test
    fun grades_target_sdk_age() {
        val manifest = node("manifest", children = listOf(node("application")))
        val modern = securityAudit(summary(manifest, targetSdk = 35))
        assertTrue(modern.find(SecurityCheck.OutdatedTargetSdk) == null)

        val aging = securityAudit(summary(manifest, targetSdk = 28))
        assertEquals(SecuritySeverity.Note, aging.find(SecurityCheck.OutdatedTargetSdk)!!.severity)

        val legacy = securityAudit(summary(manifest, targetSdk = 21))
        assertEquals(SecuritySeverity.Warning, legacy.find(SecurityCheck.OutdatedTargetSdk)!!.severity)
    }

    @Test
    fun reports_missing_manifest_instead_of_auditing_nothing() {
        val findings = securityAudit(summary(manifest = null))
        assertEquals(1, findings.size)
        assertEquals(SecurityCheck.NoManifest, findings.single().check)
    }

    @Test
    fun quiet_manifest_yields_no_noise() {
        val manifest = node(
            "manifest",
            children = listOf(node("application", attrs = listOf("android:allowBackup" to "false"))),
        )
        assertTrue(securityAudit(summary(manifest)).isEmpty())
    }
}
