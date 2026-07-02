package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSnapshotTest {

    private fun summary(
        packageName: String? = "com.example.app",
        versionCode: Long? = 2L,
        permissions: List<String> = emptyList(),
        uncompressed: Long = 1000L,
    ) = ApkSummary(
        packageName = packageName,
        appLabel = null,
        versionName = "2.0",
        versionCode = versionCode,
        minSdk = 24,
        targetSdk = 35,
        compileSdk = null,
        permissions = permissions,
        entries = listOf(ArchiveEntry("classes.dex", 10, 5, false, CompressionMethod.Deflated)),
        dexCount = 1,
        hasResourcesArsc = false,
        hasBinaryManifest = false,
        signatureFiles = emptyList(),
        signingInfo = null,
        totalUncompressedBytes = uncompressed,
        totalCompressedBytes = uncompressed / 2,
        dexStats = DexStats(
            listOf(DexFileStats("classes.dex", 1, 1, 1, 1, methodIds = 500, classDefs = 40, sizeBytes = 10)),
        ),
    )

    private fun snapshot(
        versionCode: Long,
        permissions: List<String> = emptyList(),
        bytes: Long = 1000L,
        methods: Int = 500,
    ) = AnalysisSnapshot(
        packageName = "com.example.app",
        versionName = "v$versionCode",
        versionCode = versionCode,
        analyzedAt = versionCode * 10,
        uncompressedBytes = bytes,
        entryCount = 1,
        permissions = permissions,
        dexMethodRefs = methods,
        dexClasses = 40,
    )

    @Test
    fun snapshots_capture_the_diffable_facts() {
        val snap = snapshotOf(summary(permissions = listOf("b", "a")), analyzedAt = 99L)
        assertNotNull(snap)
        snap!!
        assertEquals("com.example.app", snap.packageName)
        assertEquals(99L, snap.analyzedAt)
        assertEquals(listOf("a", "b"), snap.permissions) // stored sorted
        assertEquals(500, snap.dexMethodRefs)
    }

    @Test
    fun unidentifiable_archives_produce_no_snapshot() {
        assertNull(snapshotOf(summary(packageName = null), analyzedAt = 1L))
        assertNull(snapshotOf(summary(versionCode = null), analyzedAt = 1L))
    }

    @Test
    fun delta_reports_permission_and_size_changes() {
        val old = snapshot(versionCode = 1, permissions = listOf("android.permission.CAMERA"), bytes = 1000, methods = 500)
        val new = snapshot(
            versionCode = 2,
            permissions = listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"),
            bytes = 1500,
            methods = 700,
        )
        val delta = snapshotDelta(old, new)!!
        assertEquals(500L, delta.sizeDeltaBytes)
        assertEquals(200, delta.methodRefsDelta)
        assertEquals(listOf("android.permission.RECORD_AUDIO"), delta.addedPermissions)
        assertTrue(delta.removedPermissions.isEmpty())
        assertTrue(delta.hasChanges)
    }

    @Test
    fun no_delta_without_a_comparable_previous_version() {
        val current = snapshot(versionCode = 2)
        assertNull(snapshotDelta(null, current))
        assertNull(snapshotDelta(snapshot(versionCode = 2), current)) // same build
        assertNull(
            snapshotDelta(snapshot(versionCode = 1).copy(packageName = "other.app"), current),
        )
    }

    @Test
    fun identical_builds_under_different_version_codes_report_no_changes() {
        val delta = snapshotDelta(snapshot(versionCode = 1), snapshot(versionCode = 2))!!
        assertFalse(delta.hasChanges)
    }
}
