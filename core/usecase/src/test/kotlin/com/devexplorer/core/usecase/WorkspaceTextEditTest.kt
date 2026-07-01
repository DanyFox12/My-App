package com.devexplorer.core.usecase

import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.WorkspaceItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTextEditTest {

    private fun item(id: String) = WorkspaceItem(
        id = id,
        name = id,
        ref = StorageRef.workspace(id),
        sizeBytes = 0L,
        addedAt = 0L,
    )

    @Test
    fun saves_then_reads_back_edited_text() = runTest {
        val workspace = FakeWorkspaceRepository()
        val target = item("notes.txt")
        // Seed an existing file (as if imported/extracted earlier).
        workspace.contents[target.id] = "original".toByteArray()

        val save = SaveWorkspaceTextUseCase(workspace, FakeWriteCapability)
        val read = ReadWorkspaceTextUseCase(workspace)

        val saved = save(target, "edited content")
        assertTrue(saved.isSuccess)
        assertEquals("edited content".length.toLong(), saved.getOrThrow().sizeBytes)

        val readBack = read(target)
        assertEquals("edited content", readBack.getOrThrow())
    }

    @Test
    fun reading_a_missing_item_fails() = runTest {
        val read = ReadWorkspaceTextUseCase(FakeWorkspaceRepository())
        assertTrue(read(item("ghost.txt")).isFailure)
    }
}
