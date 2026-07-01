package com.devexplorer.core.usecase

import com.devexplorer.core.capability.ApkRepository
import com.devexplorer.core.capability.PackagesRepository
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.InstalledPackage
import com.devexplorer.core.model.PermissionUsage
import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.InputStream
import org.junit.Test

/** ApkRepository double: maps the first byte of the stream to a preset summary. */
private class FakeApkRepository(private val byMarker: Map<Int, ApkSummary>) : ApkRepository {
    override suspend fun analyze(input: InputStream): ApkSummary {
        val marker = input.read()
        return byMarker.getValue(marker)
    }
}

private class UnusedPackagesRepository : PackagesRepository {
    override suspend fun listInstalled(includeSystem: Boolean): List<InstalledPackage> = emptyList()
    override suspend fun permissionUsage(includeSystem: Boolean): List<PermissionUsage> = emptyList()
    override suspend fun openApk(packageName: String): InputStream = throw NotImplementedError()
}

class CompareApkUseCaseTest {

    private fun summary(perms: List<String>) = ApkSummary(
        packageName = "com.example.app",
        appLabel = "Example",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 24,
        targetSdk = 35,
        compileSdk = 35,
        permissions = perms,
        entries = emptyList(),
        dexCount = 0,
        hasResourcesArsc = false,
        hasBinaryManifest = false,
        signatureFiles = emptyList(),
        signingInfo = null,
        totalUncompressedBytes = 0L,
        totalCompressedBytes = 0L,
    )

    @Test
    fun analyzes_both_sides_and_diffs_them() = runTest {
        val oldRef = StorageRef.safDocument("old")
        val newRef = StorageRef.safDocument("new")
        val storage = FakeStorageRepository(
            streams = mapOf(
                oldRef.raw to byteArrayOf(1),
                newRef.raw to byteArrayOf(2),
            ),
        )
        val apk = FakeApkRepository(
            mapOf(
                1 to summary(listOf("android.permission.CAMERA")),
                2 to summary(listOf("android.permission.CAMERA", "android.permission.INTERNET")),
            ),
        )
        val compare = CompareApkUseCase(AnalyzeApkUseCase(storage, UnusedPackagesRepository(), apk))

        val result = compare(oldRef, newRef)

        assertTrue(result.isSuccess)
        val comparison = result.getOrThrow()
        assertEquals(listOf("android.permission.INTERNET"), comparison.diff.permissionsAdded)
        assertTrue(comparison.diff.permissionsRemoved.isEmpty())
    }
}
