package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BinaryXmlTest {

    /** Builds a minimal UTF-8 string-pool chunk matching the decoder's reader. */
    private fun buildUtf8Pool(strings: List<String>): ByteArray {
        val data = ArrayList<Byte>()
        val offsets = ArrayList<Int>()
        for (s in strings) {
            offsets.add(data.size)
            val b = s.toByteArray(Charsets.UTF_8)
            data.add(s.length.toByte()) // char count (ASCII, < 128)
            data.add(b.size.toByte())   // byte count (< 128)
            data.addAll(b.toList())
            data.add(0)                 // null terminator
        }
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out.add((v and 0xFF).toByte()); out.add(((v shr 8) and 0xFF).toByte()) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v ushr 16) and 0xFFFF) }
        val stringsStart = 28 + strings.size * 4
        u16(0x0001); u16(28); u32(stringsStart + data.size) // type, headerSize, size
        u32(strings.size); u32(0); u32(0x100); u32(stringsStart); u32(0)
        offsets.forEach { u32(it) }
        out.addAll(data)
        return out.toByteArray()
    }

    @Test
    fun parses_utf8_string_pool() {
        val bytes = buildUtf8Pool(listOf("manifest", "package", "com.example.app"))
        val pool = BinaryXml.parseStringPool(bytes, 0)
        assertEquals(listOf("manifest", "package", "com.example.app"), pool)
    }

    @Test
    fun reads_utf16_string() {
        // len=2 (u16), then "hi" as UTF-16LE, then a null terminator.
        val bytes = byteArrayOf(
            0x02, 0x00,
            'h'.code.toByte(), 0x00,
            'i'.code.toByte(), 0x00,
            0x00, 0x00,
        )
        assertEquals("hi", BinaryXml.readUtf16(bytes, 0))
    }

    @Test
    fun formats_typed_values() {
        val pool = listOf("first", "second")
        assertEquals("second", BinaryXml.formatValue(0x03, data = 1, rawIndex = 0, pool = pool)) // string
        assertEquals("true", BinaryXml.formatValue(0x12, data = 1, rawIndex = 0, pool = pool))   // boolean
        assertEquals("false", BinaryXml.formatValue(0x12, data = 0, rawIndex = 0, pool = pool))
        assertEquals("42", BinaryXml.formatValue(0x10, data = 42, rawIndex = 0, pool = pool))     // int dec
        assertEquals("0xff", BinaryXml.formatValue(0x11, data = 255, rawIndex = 0, pool = pool))  // int hex
        assertEquals("@0x7f010001", BinaryXml.formatValue(0x01, data = 0x7f010001, rawIndex = 0, pool = pool))
    }
}
