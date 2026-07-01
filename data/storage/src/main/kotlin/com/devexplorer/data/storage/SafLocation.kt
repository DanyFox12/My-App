package com.devexplorer.data.storage

import com.devexplorer.core.model.StorageRef

/**
 * A SAF location needs TWO pieces of information to be navigable: the original
 * *tree* Uri the user granted (which carries the permission), and the *document
 * id* of the specific folder/file within that tree. The document layer never
 * lets you "escape" the granted tree — every child document is addressed
 * relative to it.
 *
 * The domain's [StorageRef] only has a single opaque [StorageRef.raw] string, so
 * we pack both values into it here and unpack them at the data boundary. The
 * domain stays framework-free and unaware of this encoding.
 */
internal data class SafLocation(
    val treeUri: String,
    val documentId: String,
) {
    fun encode(): String = treeUri + SEPARATOR + documentId

    companion object {
        // A control character that cannot appear in a Uri or a document id.
        private const val SEPARATOR = '\u0001'

        fun decode(raw: String): SafLocation {
            val idx = raw.indexOf(SEPARATOR)
            require(idx >= 0) { "Not a SAF-encoded StorageRef: missing separator" }
            return SafLocation(
                treeUri = raw.substring(0, idx),
                documentId = raw.substring(idx + 1),
            )
        }

        fun decode(ref: StorageRef): SafLocation = decode(ref.raw)
    }
}
