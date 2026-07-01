package com.devexplorer.core.usecase

import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class ExtractEntryUseCaseTest {

    /** Build an in-memory ZIP with the given name -> content entries. */
    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val archive = zipOf(
        mapOf(
            "AndroidManifest.xml" to "manifest-bytes".toByteArray(),
            "res/layout/main.xml" to "<layout/>".toByteArray(),
            "classes.dex" to "dex-bytes".toByteArray(),
        ),
    )

    @Test
    fun extracts_a_nested_entry_under_its_leaf_name() = runTest {
        val ref = StorageRef.safDocument("app.apk")
        val storage = FakeStorageRepository(streams = mapOf(ref.raw to archive))
        val workspace = FakeWorkspaceRepository()
        val useCase = ExtractEntryUseCase(storage, FakePackagesRepository(), workspace, FakeWriteCapability)

        val result = useCase(ref, "res/layout/main.xml")

        assertTrue(result.isSuccess)
        assertEquals(1, workspace.imported.size)
        assertEquals("main.xml", workspace.imported[0].first)
        assertEquals("<layout/>", String(workspace.imported[0].second))
    }

    @Test
    fun extracts_from_an_installed_package_source() = runTest {
        val ref = StorageRef.installedPackage("com.example.app")
        val packages = FakePackagesRepository(mapOf("com.example.app" to archive))
        val workspace = FakeWorkspaceRepository()
        val useCase = ExtractEntryUseCase(FakeStorageRepository(), packages, workspace, FakeWriteCapability)

        val result = useCase(ref, "classes.dex")

        assertTrue(result.isSuccess)
        assertEquals("classes.dex", workspace.imported[0].first)
        assertEquals("dex-bytes", String(workspace.imported[0].second))
    }

    @Test
    fun fails_when_the_entry_is_absent() = runTest {
        val ref = StorageRef.safDocument("app.apk")
        val storage = FakeStorageRepository(streams = mapOf(ref.raw to archive))
        val useCase = ExtractEntryUseCase(
            storage,
            FakePackagesRepository(),
            FakeWorkspaceRepository(),
            FakeWriteCapability,
        )

        val result = useCase(ref, "does/not/exist.bin")

        assertTrue(result.isFailure)
    }
}
