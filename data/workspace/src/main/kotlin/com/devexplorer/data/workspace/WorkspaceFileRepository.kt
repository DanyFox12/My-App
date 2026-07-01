package com.devexplorer.data.workspace

import android.content.Context
import com.devexplorer.core.capability.WorkspaceRepository
import com.devexplorer.core.capability.WriteCapability
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.WorkspaceItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * The one and only writer in the app.
 *
 * Every write lands inside [workspaceDir] = `filesDir/workspace`, which is
 * app-private storage requiring NO permission (that's the point of scoped
 * storage: your own sandbox is always yours). Two invariants keep it safe:
 *
 *  1. **Containment** — [requireInside] canonicalizes every target path and
 *     rejects anything that resolves outside the sandbox root. This defeats
 *     directory-traversal attempts like a display name of "../../databases/x".
 *  2. **Name sanitization** — incoming display names are stripped of any path
 *     components before use.
 *
 * The [WriteCapability] parameters are compile-time proof that the caller was
 * allowed to reach a write path; they aren't otherwise used at runtime.
 */
class WorkspaceFileRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceRepository {

    private val appContext = context.applicationContext
    private val workspaceDir: File get() = File(appContext.filesDir, WORKSPACE_DIR)

    override suspend fun list(): List<WorkspaceItem> = withContext(io) {
        ensureDir()
        workspaceDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.toItem() }
            ?: emptyList()
    }

    override suspend fun importStream(
        displayName: String,
        input: InputStream,
        capability: WriteCapability,
    ): WorkspaceItem = withContext(io) {
        ensureDir()
        val target = uniqueFile(sanitize(displayName))
        requireInside(target)
        FileOutputStream(target).use { output -> input.copyTo(output) }
        target.toItem()
    }

    override suspend fun rename(
        item: WorkspaceItem,
        newName: String,
        capability: WriteCapability,
    ): WorkspaceItem = withContext(io) {
        val source = File(workspaceDir, item.id)
        requireInside(source)
        if (!source.exists()) throw IOException("Item no longer exists")
        val target = uniqueFile(sanitize(newName))
        requireInside(target)
        if (!source.renameTo(target)) throw IOException("Rename failed")
        target.toItem()
    }

    override suspend fun delete(item: WorkspaceItem, capability: WriteCapability) {
        withContext(io) {
            val file = File(workspaceDir, item.id)
            requireInside(file)
            if (file.exists() && !file.delete()) throw IOException("Delete failed")
        }
    }

    // --- helpers ---

    private fun ensureDir() {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
    }

    private fun File.toItem(): WorkspaceItem = WorkspaceItem(
        id = name,
        name = name,
        ref = StorageRef.workspace(name),
        sizeBytes = length(),
        addedAt = lastModified(),
    )

    /** Strip any path separators; keep only a plain, safe file name. */
    private fun sanitize(rawName: String): String {
        val base = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .ifBlank { "file" }
        // Disallow the traversal names outright.
        return if (base == "." || base == "..") "file" else base
    }

    /** Return a File that doesn't collide, appending " (n)" before the extension. */
    private fun uniqueFile(name: String): File {
        var candidate = File(workspaceDir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(workspaceDir, "$stem ($n)$ext")
            n++
        }
        return candidate
    }

    /** Reject any file whose canonical path escapes the sandbox root. */
    private fun requireInside(file: File) {
        val root = workspaceDir.canonicalPath
        val path = file.canonicalPath
        if (path != root && !path.startsWith(root + File.separator)) {
            throw SecurityException("Path escapes the Workspace sandbox")
        }
    }

    private companion object {
        const val WORKSPACE_DIR = "workspace"
    }
}
