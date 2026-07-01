package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/** A decoded XML element from a binary AndroidManifest.xml. */
@Serializable
data class XmlNode(
    val name: String,
    val attributes: List<XmlAttribute>,
    val children: List<XmlNode>,
)

/** A decoded XML attribute (name already prefixed, e.g. "android:name"). */
@Serializable
data class XmlAttribute(
    val name: String,
    val value: String,
)

/**
 * A from-scratch decoder for Android's **binary XML** (AXML) format — the way
 * AndroidManifest.xml is actually stored inside an APK (not text!).
 *
 * The format is a series of little-endian chunks: a string pool, an optional
 * resource map, then a stream of start/end element and namespace events. We parse
 * the string pool (UTF-8 or UTF-16), then walk the element events with a stack to
 * rebuild the tree, resolving typed attribute values and namespace prefixes.
 *
 * It is deliberately **fail-soft**: any malformed input returns null instead of
 * throwing, so a weird APK degrades gracefully.
 */
object BinaryXml {

    // Chunk types.
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_NAMESPACE = 0x0100
    private const val RES_XML_START_ELEMENT = 0x0102
    private const val RES_XML_END_ELEMENT = 0x0103

    fun decode(bytes: ByteArray): XmlNode? = runCatching { decodeOrThrow(bytes) }.getOrNull()

    private fun decodeOrThrow(bytes: ByteArray): XmlNode? {
        if (bytes.size < 8 || u16(bytes, 0) != RES_XML_TYPE) return null

        var pos = u16(bytes, 2).coerceAtLeast(8) // skip the file chunk header
        var pool: List<String> = emptyList()
        val nsPrefixByUri = HashMap<String, String>()
        val stack = ArrayDeque<MutableNode>()
        var root: MutableNode? = null

        while (pos + 8 <= bytes.size) {
            val type = u16(bytes, pos)
            val size = u32(bytes, pos + 4).toInt()
            if (size <= 0) break

            when (type) {
                RES_STRING_POOL_TYPE -> pool = parseStringPool(bytes, pos)

                RES_XML_START_NAMESPACE -> {
                    val prefix = pool.getOrNull(u32(bytes, pos + 16).toInt())
                    val uri = pool.getOrNull(u32(bytes, pos + 20).toInt())
                    if (prefix != null && uri != null) nsPrefixByUri[uri] = prefix
                }

                RES_XML_START_ELEMENT -> {
                    val name = pool.getOrNull(u32(bytes, pos + 20).toInt()) ?: "?"
                    val attrStart = u16(bytes, pos + 24)
                    val attrSize = u16(bytes, pos + 26)
                    val attrCount = u16(bytes, pos + 28)

                    val attributes = ArrayList<XmlAttribute>(attrCount)
                    var ap = pos + 16 + attrStart
                    repeat(attrCount) {
                        val aNsUri = pool.getOrNull(u32(bytes, ap).toInt())
                        val aName = pool.getOrNull(u32(bytes, ap + 4).toInt()).orEmpty()
                        val rawIdx = u32(bytes, ap + 8).toInt()
                        val dataType = u8(bytes, ap + 15)
                        val data = u32(bytes, ap + 16).toInt()
                        if (aName.isNotEmpty()) {
                            val prefix = aNsUri?.let { nsPrefixByUri[it] }
                            val display = if (prefix != null) "$prefix:$aName" else aName
                            attributes += XmlAttribute(display, formatValue(dataType, data, rawIdx, pool))
                        }
                        ap += attrSize
                    }

                    val node = MutableNode(name, attributes)
                    if (root == null) root = node
                    stack.lastOrNull()?.children?.add(node)
                    stack.addLast(node)
                }

                RES_XML_END_ELEMENT -> stack.removeLastOrNull()
            }
            pos += size
        }
        return root?.toImmutable()
    }

    // --- string pool ---

    internal fun parseStringPool(bytes: ByteArray, chunkStart: Int): List<String> {
        val stringCount = u32(bytes, chunkStart + 8).toInt()
        val flags = u32(bytes, chunkStart + 16).toInt()
        val stringsStart = u32(bytes, chunkStart + 20).toInt()
        val utf8 = (flags and (1 shl 8)) != 0
        val offsetsBase = chunkStart + 28
        val dataBase = chunkStart + stringsStart

        return (0 until stringCount).map { i ->
            val strOffset = u32(bytes, offsetsBase + i * 4).toInt()
            val p = dataBase + strOffset
            if (utf8) readUtf8(bytes, p) else readUtf16(bytes, p)
        }
    }

    internal fun readUtf16(bytes: ByteArray, start: Int): String {
        var p = start
        var len = u16(bytes, p)
        p += 2
        if (len and 0x8000 != 0) {
            len = ((len and 0x7FFF) shl 16) or u16(bytes, p)
            p += 2
        }
        return String(bytes, p, len * 2, Charsets.UTF_16LE)
    }

    internal fun readUtf8(bytes: ByteArray, start: Int): String {
        var p = start
        // character count (unused) then byte count, each 1–2 bytes.
        var charLen = u8(bytes, p); p++
        if (charLen and 0x80 != 0) { charLen = ((charLen and 0x7F) shl 8) or u8(bytes, p); p++ }
        var byteLen = u8(bytes, p); p++
        if (byteLen and 0x80 != 0) { byteLen = ((byteLen and 0x7F) shl 8) or u8(bytes, p); p++ }
        return String(bytes, p, byteLen, Charsets.UTF_8)
    }

    // --- typed value formatting ---

    internal fun formatValue(dataType: Int, data: Int, rawIndex: Int, pool: List<String>): String = when (dataType) {
        0x03 -> pool.getOrNull(data).orEmpty()                 // TYPE_STRING
        0x01 -> "@0x%08x".format(data)                          // TYPE_REFERENCE
        0x02 -> "?0x%08x".format(data)                          // TYPE_ATTRIBUTE
        0x12 -> (data != 0).toString()                          // TYPE_INT_BOOLEAN
        0x10 -> data.toString()                                 // TYPE_INT_DEC
        0x11 -> "0x%x".format(data)                             // TYPE_INT_HEX
        0x04 -> Float.fromBits(data).toString()                // TYPE_FLOAT
        0x05 -> complexToString(data, fraction = false)        // TYPE_DIMENSION
        0x06 -> complexToString(data, fraction = true)         // TYPE_FRACTION
        in 0x1c..0x1f -> "#%08x".format(data)                  // color types
        else -> pool.getOrNull(rawIndex) ?: "0x%x".format(data)
    }

    private val RADIX_MULTS = floatArrayOf(
        0.00390625f,        // 1/256
        3.051758e-05f,      // 1/256 / 128
        1.192093e-07f,      // 1/256 / 32768
        4.656613e-10f,      // 1/256 / 8388608
    )
    private val DIMENSION_UNITS = arrayOf("px", "dip", "sp", "pt", "in", "mm")

    private fun complexToString(complex: Int, fraction: Boolean): String {
        val value = (complex and -0x100).toFloat() * RADIX_MULTS[(complex shr 4) and 0x3]
        val unit = complex and 0xF
        val suffix = if (fraction) {
            if (unit == 0) "%" else "%p"
        } else {
            DIMENSION_UNITS.getOrElse(unit) { "" }
        }
        val number = if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
        return number + suffix
    }

    // --- unsigned little-endian readers ---

    private fun u8(b: ByteArray, p: Int): Int = b[p].toInt() and 0xFF
    private fun u16(b: ByteArray, p: Int): Int = u8(b, p) or (u8(b, p + 1) shl 8)
    private fun u32(b: ByteArray, p: Int): Long = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)

    private class MutableNode(val name: String, val attributes: List<XmlAttribute>) {
        val children = ArrayList<MutableNode>()
        fun toImmutable(): XmlNode = XmlNode(name, attributes, children.map { it.toImmutable() })
    }
}
