package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * An immutable description of one entry in a directory listing.
 *
 * This is a pure data snapshot — it carries no live file handle, so it can be
 * safely held in UI state, cached, or passed across module boundaries. The data
 * layer produces these; the UI only ever reads them.
 */
@Serializable
data class FileNode(
    val ref: StorageRef,
    val name: String,
    val isDirectory: Boolean,
    /** Size in bytes; null when unknown (e.g. a directory or an opaque provider). */
    val sizeBytes: Long? = null,
    /** Epoch millis of last modification; null when the provider doesn't report it. */
    val lastModified: Long? = null,
    /** MIME type as reported by the provider, e.g. "application/vnd.android.package-archive". */
    val mimeType: String? = null,
) {
    /** Lightweight classification used by the UI to pick an icon and a destination. */
    val category: FileCategory
        get() = when {
            isDirectory -> FileCategory.Directory
            mimeType == APK_MIME || name.endsWith(".apk", ignoreCase = true) -> FileCategory.Apk
            name.endsWith(".zip", true) || name.endsWith(".jar", true) ||
                name.endsWith(".aar", true) -> FileCategory.Archive
            isLikelyText(name) -> FileCategory.Text
            else -> FileCategory.Other
        }

    private fun isLikelyText(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0) return false
        return name.substring(dot + 1).lowercase() in TEXT_EXTENSIONS
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        private val TEXT_EXTENSIONS = setOf(
            "txt", "kt", "kts", "java", "xml", "json", "gradle", "md", "yml",
            "yaml", "properties", "html", "css", "js", "py", "c", "cpp", "h",
            "sh", "toml", "cfg", "ini", "log",
        )
    }
}

enum class FileCategory {
    Directory, Apk, Archive, Text, Other,
}
