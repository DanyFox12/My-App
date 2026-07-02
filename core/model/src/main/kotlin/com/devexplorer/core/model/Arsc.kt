package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * The global string pool of a `resources.arsc`, capped for display. The pool
 * holds every string *value* in the app's resources (all locales together) —
 * a fast, revealing x-ray of what a package ships without decoding the full
 * resource table.
 */
@Serializable
data class ArscStrings(
    val strings: List<String>,
    /** Total strings in the pool; larger than [strings].size when capped. */
    val totalCount: Int,
) {
    val truncated: Boolean get() = strings.size < totalCount
}

/**
 * Reader for the top of the **resource table** (`resources.arsc`) binary
 * format: the RES_TABLE chunk header followed by the global string pool. We
 * reuse the same string-pool decoder as the binary-XML manifest ([BinaryXml])
 * — it's literally the same chunk format.
 *
 * Deliberately **fail-soft**: malformed input returns null instead of throwing.
 */
object Arsc {

    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_STRING_POOL_TYPE = 0x0001

    /** Cap on the strings kept for display; the pool can hold hundreds of thousands. */
    const val DISPLAY_CAP: Int = 5_000

    fun readGlobalStrings(bytes: ByteArray, cap: Int = DISPLAY_CAP): ArscStrings? =
        runCatching { readOrThrow(bytes, cap) }.getOrNull()

    private fun readOrThrow(bytes: ByteArray, cap: Int): ArscStrings? {
        if (bytes.size < 12 || u16(bytes, 0) != RES_TABLE_TYPE) return null
        var pos = u16(bytes, 2) // table header size -> first child chunk
        while (pos + 8 <= bytes.size) {
            val type = u16(bytes, pos)
            val size = u32(bytes, pos + 4).toInt()
            if (size <= 0) return null
            if (type == RES_STRING_POOL_TYPE) {
                val total = u32(bytes, pos + 8).toInt()
                val strings = BinaryXml.parseStringPool(bytes, pos, limit = cap)
                return ArscStrings(strings = strings, totalCount = total)
            }
            pos += size
        }
        return null
    }

    private fun u8(b: ByteArray, p: Int): Int = b[p].toInt() and 0xFF
    private fun u16(b: ByteArray, p: Int): Int = u8(b, p) or (u8(b, p + 1) shl 8)
    private fun u32(b: ByteArray, p: Int): Long = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
}
