package com.devexplorer.core.usecase

import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.capability.WorkspaceRepository
import com.devexplorer.core.capability.WriteCapability
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.WorkspaceItem
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Test double for read-only storage. */
class FakeStorageRepository(
    private val children: Map<String, List<FileNode>> = emptyMap(),
    private val streams: Map<String, ByteArray> = emptyMap(),
) : StorageRepository {
    override fun treeRootRef(treeUri: String) = StorageRef.safTree(treeUri)
    override suspend fun persistReadPermission(treeUri: String) = Unit
    override suspend fun listChildren(parent: StorageRef): List<FileNode> =
        children[parent.raw] ?: emptyList()
    override suspend fun displayName(ref: StorageRef): String = ref.raw
    override suspend fun openInputStream(ref: StorageRef): InputStream =
        ByteArrayInputStream(streams[ref.raw] ?: ByteArray(0))
}

/** Test double for the sandbox writer; records what was imported. */
class FakeWorkspaceRepository : WorkspaceRepository {
    val imported = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun list(): List<WorkspaceItem> = emptyList()

    override suspend fun importStream(
        displayName: String,
        input: InputStream,
        capability: WriteCapability,
    ): WorkspaceItem {
        val bytes = input.readBytes()
        imported += displayName to bytes
        return WorkspaceItem(
            id = displayName,
            name = displayName,
            ref = StorageRef.workspace(displayName),
            sizeBytes = bytes.size.toLong(),
            addedAt = 0L,
        )
    }

    override suspend fun rename(item: WorkspaceItem, newName: String, capability: WriteCapability) =
        throw NotImplementedError()

    override suspend fun delete(item: WorkspaceItem, capability: WriteCapability) = Unit
}

/** A stand-in write token for tests. */
object FakeWriteCapability : WriteCapability

fun fileNode(name: String) = FileNode(StorageRef.safDocument(name), name, isDirectory = false)
fun dirNode(name: String) = FileNode(StorageRef.safTree(name), name, isDirectory = true)
