package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * A decoded text document read from a file. [truncated] is true when the file was
 * larger than the read cap (we never load an unbounded file into memory — that
 * would risk OutOfMemoryError on low-heap devices).
 */
@Serializable
data class TextDocument(
    val content: String,
    val truncated: Boolean,
    val byteSize: Long,
) {
    val lineCount: Int get() = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1

    /** Heuristic: a NUL byte in the first chunk means this isn't really text. */
    val looksBinary: Boolean get() = content.take(4096).contains('\u0000')
}
