package com.devexplorer.core.usecase

import com.devexplorer.core.capability.AnalysisHistoryRepository
import com.devexplorer.core.model.AnalysisSnapshot
import com.devexplorer.core.model.ApkSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeAnalysisHistoryRepository : AnalysisHistoryRepository {
    val recorded = mutableListOf<AnalysisSnapshot>()
    private val flow = MutableStateFlow<List<AnalysisSnapshot>>(emptyList())

    override fun observe(): Flow<List<AnalysisSnapshot>> = flow

    override suspend fun latestFor(packageName: String): AnalysisSnapshot? =
        recorded.filter { it.packageName == packageName }.maxByOrNull { it.analyzedAt }

    override suspend fun latestOtherVersion(packageName: String, versionCode: Long): AnalysisSnapshot? =
        recorded.filter { it.packageName == packageName && it.versionCode != versionCode }
            .maxByOrNull { it.analyzedAt }

    override suspend fun record(snapshot: AnalysisSnapshot) {
        recorded += snapshot
        flow.value = recorded.sortedByDescending { it.analyzedAt }
    }
}

class TrackAnalysisUseCaseTest {

    private fun summary(versionCode: Long?, permissions: List<String> = emptyList()) = ApkSummary(
        packageName = if (versionCode == null) null else "com.example.app",
        appLabel = null,
        versionName = versionCode?.toString(),
        versionCode = versionCode,
        minSdk = 24,
        targetSdk = 35,
        compileSdk = null,
        permissions = permissions,
        entries = emptyList(),
        dexCount = 0,
        hasResourcesArsc = false,
        hasBinaryManifest = false,
        signatureFiles = emptyList(),
        signingInfo = null,
        totalUncompressedBytes = versionCode ?: 0L,
        totalCompressedBytes = 0L,
    )

    @Test
    fun first_analysis_records_but_yields_no_delta() = runTest {
        val history = FakeAnalysisHistoryRepository()
        val track = TrackAnalysisUseCase(history)

        val delta = track(summary(versionCode = 1), analyzedAt = 10L).getOrThrow()
        assertNull(delta)
        assertEquals(1, history.recorded.size)
    }

    @Test
    fun version_change_yields_a_delta_against_the_previous_snapshot() = runTest {
        val history = FakeAnalysisHistoryRepository()
        val track = TrackAnalysisUseCase(history)

        track(summary(versionCode = 1), analyzedAt = 10L).getOrThrow()
        val delta = track(
            summary(versionCode = 2, permissions = listOf("android.permission.CAMERA")),
            analyzedAt = 20L,
        ).getOrThrow()

        assertEquals(1L, delta!!.previous.versionCode)
        assertEquals(2L, delta.current.versionCode)
        assertEquals(listOf("android.permission.CAMERA"), delta.addedPermissions)
    }

    @Test
    fun reanalyzing_the_same_version_neither_rerecords_nor_buries_the_delta() = runTest {
        val history = FakeAnalysisHistoryRepository()
        val track = TrackAnalysisUseCase(history)

        track(summary(versionCode = 1), analyzedAt = 10L).getOrThrow()
        track(summary(versionCode = 2), analyzedAt = 20L).getOrThrow()
        assertEquals(2, history.recorded.size)

        // A second look at v2 still reports the change from v1.
        val delta = track(summary(versionCode = 2), analyzedAt = 30L).getOrThrow()
        assertEquals(2, history.recorded.size)
        assertEquals(1L, delta!!.previous.versionCode)
    }

    @Test
    fun unidentifiable_archives_are_ignored() = runTest {
        val history = FakeAnalysisHistoryRepository()
        val track = TrackAnalysisUseCase(history)
        assertNull(track(summary(versionCode = null), analyzedAt = 10L).getOrThrow())
        assertEquals(0, history.recorded.size)
    }
}
