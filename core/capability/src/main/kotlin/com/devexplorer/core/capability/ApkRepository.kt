package com.devexplorer.core.capability

import com.devexplorer.core.model.ApkSummary
import java.io.InputStream

/**
 * READ-ONLY analysis of an APK/ZIP.
 *
 * The implementation ([:data:apk]) holds only [ReadCapability]. It reads the
 * archive's bytes and the platform's package metadata, and returns an immutable
 * [ApkSummary]. There is no method here that could modify the source — and,
 * because the module can't obtain a [WriteCapability], it couldn't write even if
 * a method tried to.
 */
interface ApkRepository {
    /**
     * Analyze an archive supplied as a read-only [input] stream. The caller owns
     * and closes the stream. The implementation may stage a private temp copy
     * (in its own cache) so the platform package parser can read it; that copy is
     * an internal detail and is deleted before returning.
     */
    suspend fun analyze(input: InputStream): ApkSummary
}
