package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * Parsed counts from a single `classes*.dex` file's header.
 *
 * A DEX file begins with a fixed 0x70-byte header whose size fields tell you,
 * without walking the whole file, how many strings/types/methods/fields/classes
 * it defines. [methodIds] is the number of *method references* — the value that
 * runs into Android's well-known 65,536-per-DEX ceiling (the reason multidex
 * exists). Surfacing it turns "why is this app multidex?" into a fact.
 */
@Serializable
data class DexFileStats(
    /** Archive entry name, e.g. "classes.dex" or "classes2.dex". */
    val name: String,
    val stringIds: Int,
    val typeIds: Int,
    val protoIds: Int,
    val fieldIds: Int,
    val methodIds: Int,
    val classDefs: Int,
    /** Uncompressed size of the DEX entry in the archive. */
    val sizeBytes: Long,
) {
    /** How close this DEX is to the 64K method-reference ceiling, 0f..1f+. */
    val methodLoad: Float get() = methodIds.toFloat() / DexStats.METHOD_REF_LIMIT

    /** True once this DEX is close enough to the ceiling to be worth flagging. */
    val nearsMethodLimit: Boolean get() = methodIds >= DexStats.METHOD_REF_WARN
}

/**
 * Aggregated DEX view across every `classes*.dex` in an archive. Pure and
 * serializable so it rides along inside [ApkSummary] and is unit-tested here.
 */
@Serializable
data class DexStats(
    val files: List<DexFileStats>,
) {
    val dexCount: Int get() = files.size
    val totalMethods: Int get() = files.sumOf { it.methodIds }
    val totalClasses: Int get() = files.sumOf { it.classDefs }
    val totalFields: Int get() = files.sumOf { it.fieldIds }
    val totalStrings: Int get() = files.sumOf { it.stringIds }

    /** True when any single DEX is near the per-file 64K method-reference limit. */
    val nearsMethodLimit: Boolean get() = files.any { it.nearsMethodLimit }

    companion object {
        /** Android's hard per-DEX method-reference ceiling (2^16). */
        const val METHOD_REF_LIMIT: Int = 65_536

        /** Threshold at which we start warning that a DEX is running out of room. */
        const val METHOD_REF_WARN: Int = 60_000
    }
}

/**
 * A from-scratch reader for the **DEX file header** — no dexlib, no ART. The
 * header is a fixed little-endian layout; we validate the `dex\n0nn\0` magic and
 * the endian tag, then read the six *_ids_size / class_defs_size fields at their
 * documented offsets.
 *
 * Deliberately **fail-soft**: any archive entry that isn't a well-formed
 * little-endian DEX returns null instead of throwing, so a weird APK degrades
 * gracefully (matching [BinaryXml]).
 */
object Dex {

    private const val HEADER_SIZE = 0x70
    private const val ENDIAN_CONSTANT = 0x12345678L

    // Documented header offsets (see the Dalvik executable format spec).
    private const val OFF_ENDIAN_TAG = 0x28
    private const val OFF_STRING_IDS_SIZE = 0x38
    private const val OFF_TYPE_IDS_SIZE = 0x40
    private const val OFF_PROTO_IDS_SIZE = 0x48
    private const val OFF_FIELD_IDS_SIZE = 0x50
    private const val OFF_METHOD_IDS_SIZE = 0x58
    private const val OFF_CLASS_DEFS_SIZE = 0x60

    /**
     * Parse the header of one DEX file. [sizeBytes] is the entry's uncompressed
     * size from the archive (defaults to the buffer length). Returns null if the
     * bytes are too short, lack the DEX magic, or aren't little-endian.
     */
    fun parseHeader(
        name: String,
        bytes: ByteArray,
        sizeBytes: Long = bytes.size.toLong(),
    ): DexFileStats? = runCatching {
        if (bytes.size < HEADER_SIZE) return null
        if (!hasDexMagic(bytes)) return null
        if (u32(bytes, OFF_ENDIAN_TAG) != ENDIAN_CONSTANT) return null // big-endian DEX: unsupported

        DexFileStats(
            name = name,
            stringIds = u32(bytes, OFF_STRING_IDS_SIZE).toInt(),
            typeIds = u32(bytes, OFF_TYPE_IDS_SIZE).toInt(),
            protoIds = u32(bytes, OFF_PROTO_IDS_SIZE).toInt(),
            fieldIds = u32(bytes, OFF_FIELD_IDS_SIZE).toInt(),
            methodIds = u32(bytes, OFF_METHOD_IDS_SIZE).toInt(),
            classDefs = u32(bytes, OFF_CLASS_DEFS_SIZE).toInt(),
            sizeBytes = sizeBytes,
        )
    }.getOrNull()

    // Magic is "dex\n" then a 3-char version and a 0 terminator: 64 65 78 0A .. .. .. 00
    private fun hasDexMagic(b: ByteArray): Boolean =
        b[0].toInt() == 0x64 && b[1].toInt() == 0x65 && b[2].toInt() == 0x78 &&
            b[3].toInt() == 0x0A && b[7].toInt() == 0x00

    private fun u8(b: ByteArray, p: Int): Long = (b[p].toInt() and 0xFF).toLong()
    private fun u32(b: ByteArray, p: Int): Long =
        u8(b, p) or (u8(b, p + 1) shl 8) or (u8(b, p + 2) shl 16) or (u8(b, p + 3) shl 24)
}
