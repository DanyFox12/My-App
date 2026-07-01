package com.devexplorer.core.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyIntoWorkspaceUseCaseTest {

    @Test
    fun copies_file_bytes_into_the_workspace() = runTest {
        val storage = FakeStorageRepository(
            streams = mapOf("notes.txt" to "hello world".toByteArray()),
        )
        val workspace = FakeWorkspaceRepository()
        val useCase = CopyIntoWorkspaceUseCase(storage, workspace, FakeWriteCapability)

        val result = useCase.invoke(fileNode("notes.txt"))

        assertTrue(result.isSuccess)
        assertEquals(1, workspace.imported.size)
        assertEquals("notes.txt", workspace.imported[0].first)
        assertEquals("hello world", String(workspace.imported[0].second))
    }

    @Test
    fun refuses_to_copy_a_directory() = runTest {
        val useCase = CopyIntoWorkspaceUseCase(
            FakeStorageRepository(),
            FakeWorkspaceRepository(),
            FakeWriteCapability,
        )

        val result = useCase.invoke(dirNode("some_folder"))

        assertTrue(result.isFailure)
    }
}
