package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArscTest {

    /** Builds a UTF-8 string-pool chunk (same layout BinaryXmlTest exercises). */
    private fun stringPool(strings: List<String>): ByteArray {
        val data = ArrayList<Byte>()
        val offsets = ArrayList<Int>()
        for (s in strings) {
            offsets.add(data.size)
            val b = s.toByteArray(Charsets.UTF_8)
            data.add(s.length.toByte())
            data.add(b.size.toByte())
            data.addAll(b.toList())
            data.add(0)
        }
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out.add((v and 0xFF).toByte()); out.add(((v shr 8) and 0xFF).toByte()) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v ushr 16) and 0xFFFF) }
        val stringsStart = 28 + strings.size * 4
        u16(0x0001); u16(28); u32(stringsStart + data.size)
        u32(strings.size); u32(0); u32(0x100); u32(stringsStart); u32(0)
        offsets.forEach { u32(it) }
        out.addAll(data)
        return out.toByteArray()
    }

    /** Wrap a string pool in a minimal RES_TABLE chunk. */
    private fun table(pool: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out.add((v and 0xFF).toByte()); out.add(((v shr 8) and 0xFF).toByte()) }
        fun u32(v: Int) { u16(v and 0xFFFF); u16((v ushr 16) and 0xFFFF) }
        u16(0x0002); u16(12); u32(12 + pool.size) // type, headerSize, chunkSize
        u32(1) // package count
        out.addAll(pool.toList())
        return out.toByteArray()
    }

    @Test
    fun reads_the_global_string_pool() {
        val strings = listOf("app_name", "Hello world", "res/drawable/icon.png")
        val result = Arsc.readGlobalStrings(table(stringPool(strings)))!!
        assertEquals(strings, result.strings)
        assertEquals(3, result.totalCount)
        assertFalse(result.truncated)
    }

    @Test
    fun caps_huge_pools_and_reports_truncation() {
        val strings = (1..50).map { "string_$it" }
        val result = Arsc.readGlobalStrings(table(stringPool(strings)), cap = 10)!!
        assertEquals(10, result.strings.size)
        assertEquals(50, result.totalCount)
        assertTrue(result.truncated)
        assertEquals("string_1", result.strings.first())
    }

    @Test
    fun rejects_non_arsc_bytes() {
        assertNull(Arsc.readGlobalStrings(ByteArray(0)))
        assertNull(Arsc.readGlobalStrings("PK not a resource table".toByteArray()))
        // Valid type but no string pool chunk inside.
        val bare = byteArrayOf(0x02, 0x00, 0x0C, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
        assertNull(Arsc.readGlobalStrings(bare))
    }
}
