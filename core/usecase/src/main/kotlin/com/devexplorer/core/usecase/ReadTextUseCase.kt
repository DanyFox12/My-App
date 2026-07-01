package com.devexplorer.core.usecase

import com.devexplorer.core.capability.StorageRepository
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.TextDocument
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads a text file for the Code Viewer.
 *
 * Reads at most [maxBytes] (+1 to detect truncation) so a huge file can't OOM a
 * low-heap device — a deliberate performance/safety cap. Decodes as UTF-8.
 * Wrapped in [Result] so an unreadable file surfaces as UI state.
 */
class ReadTextUseCase(
    private val storage: StorageRepository,
) {
    suspend operator fun invoke(
        source: StorageRef,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): Result<TextDocument> = runCatching {
        storage.openInputStream(source).use { input ->
            val bytes = input.readUpTo(maxBytes + 1)
            val truncated = bytes.size > maxBytes
            val usable = if (truncated) bytes.copyOf(maxBytes) else bytes
            TextDocument(
                content = String(usable, Charsets.UTF_8),
                truncated = truncated,
                byteSize = bytes.size.toLong(),
            )
        }
    }

    /** Read up to [limit] bytes without relying on API-33 InputStream.readNBytes. */
    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() <= limit) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 1024 * 1024 // 1 MiB
    }
}
