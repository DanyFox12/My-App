package com.devexplorer.data.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.devexplorer.core.capability.ReadCapability
import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Storage Access Framework implementation of [StorageRepository].
 *
 * We use [ContentResolver] + [DocumentsContract] directly (rather than the
 * `DocumentFile` convenience wrapper) because seeing the real content-provider
 * calls is the whole point of this learning project:
 *
 *  - A SAF *tree* Uri grants access to a subtree the user explicitly chose.
 *  - [DocumentsContract.buildChildDocumentsUriUsingTree] builds the content Uri
 *    whose rows are the children of a folder; we `query()` it via the resolver,
 *    which crosses a Binder boundary into the documents provider process.
 *  - We never construct a Uri outside the granted tree, so we can't read
 *    anything the user didn't permit — the OS enforces it.
 *
 * This class holds [ReadCapability]: it can read, and there is no method here
 * that writes. Writing lives in a separate repository (milestone 3).
 */
class SafStorageRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StorageRepository, ReadCapability {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun treeRootRef(treeUri: String): StorageRef {
        val uri = Uri.parse(treeUri)
        // The tree Uri encodes the id of the folder the user picked.
        val rootDocId = DocumentsContract.getTreeDocumentId(uri)
        return StorageRef(
            raw = SafLocation(treeUri, rootDocId).encode(),
            kind = StorageRef.Kind.SafTree,
        )
    }

    override suspend fun persistReadPermission(treeUri: String) = withContext(ioDispatcher) {
        // Persist ONLY the read grant. The picker also offers write, but we
        // deliberately don't take it — the System zone must stay read-only.
        resolver.takePersistableUriPermission(
            Uri.parse(treeUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    override suspend fun listChildren(parent: StorageRef): List<FileNode> = withContext(ioDispatcher) {
        val loc = SafLocation.decode(parent)
        val treeUri = Uri.parse(loc.treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, loc.documentId)

        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        buildList {
            resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idxId = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val idxName = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val idxMime = c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val idxSize = c.getColumnIndexOrThrow(Document.COLUMN_SIZE)
                val idxDate = c.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val docId = c.getString(idxId) ?: continue
                    val name = c.getString(idxName) ?: continue
                    val mime = c.getString(idxMime)
                    val isDir = mime == Document.MIME_TYPE_DIR
                    add(
                        FileNode(
                            ref = StorageRef(
                                raw = SafLocation(loc.treeUri, docId).encode(),
                                kind = if (isDir) StorageRef.Kind.SafTree else StorageRef.Kind.SafDocument,
                            ),
                            name = name,
                            isDirectory = isDir,
                            sizeBytes = c.longOrNull(idxSize),
                            lastModified = c.longOrNull(idxDate),
                            mimeType = mime?.takeUnless { isDir },
                        ),
                    )
                }
            }
        }
    }

    override suspend fun displayName(ref: StorageRef): String = withContext(ioDispatcher) {
        val loc = SafLocation.decode(ref)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(loc.treeUri), loc.documentId)
        resolver.query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: DEFAULT_NAME
    }

    override suspend fun openInputStream(ref: StorageRef): InputStream = withContext(ioDispatcher) {
        val loc = SafLocation.decode(ref)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(loc.treeUri), loc.documentId)
        // Read-only stream from the documents provider. We never open for write.
        resolver.openInputStream(docUri) ?: throw IOException("Cannot open $docUri")
    }

    private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

    private companion object {
        const val DEFAULT_NAME = "Files"
    }
}
