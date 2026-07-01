package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexInfoTest {

    /** Build a minimal but valid little-endian DEX header with the given counts. */
    private fun dexHeader(
        stringIds: Int = 0,
        typeIds: Int = 0,
        protoIds: Int = 0,
        fieldIds: Int = 0,
        methodIds: Int = 0,
        classDefs: Int = 0,
        magicVersion: String = "035",
        endian: Long = 0x12345678L,
    ): ByteArray {
        val b = ByteArray(0x70)
        // magic: "dex\n" + version + 0x00
        val magic = "dex\n$magicVersion".toByteArray(Charsets.US_ASCII)
        magic.copyInto(b, 0)
        b[7] = 0
        fun putU32(off: Int, value: Long) {
            b[off] = (value and 0xFF).toByte()
            b[off + 1] = ((value shr 8) and 0xFF).toByte()
            b[off + 2] = ((value shr 16) and 0xFF).toByte()
            b[off + 3] = ((value shr 24) and 0xFF).toByte()
        }
        putU32(0x28, endian)
        putU32(0x38, stringIds.toLong())
        putU32(0x40, typeIds.toLong())
        putU32(0x48, protoIds.toLong())
        putU32(0x50, fieldIds.toLong())
        putU32(0x58, methodIds.toLong())
        putU32(0x60, classDefs.toLong())
        return b
    }

    @Test
    fun parses_counts_from_a_valid_header() {
        val stats = Dex.parseHeader(
            "classes.dex",
            dexHeader(stringIds = 12, typeIds = 5, protoIds = 7, fieldIds = 9, methodIds = 42, classDefs = 3),
            sizeBytes = 1234L,
        )
        assertNotNull(stats)
        stats!!
        assertEquals("classes.dex", stats.name)
        assertEquals(12, stats.stringIds)
        assertEquals(5, stats.typeIds)
        assertEquals(7, stats.protoIds)
        assertEquals(9, stats.fieldIds)
        assertEquals(42, stats.methodIds)
        assertEquals(3, stats.classDefs)
        assertEquals(1234L, stats.sizeBytes)
    }

    @Test
    fun rejects_wrong_magic() {
        val bytes = dexHeader(methodIds = 10).copyOf()
        bytes[0] = 'X'.code.toByte()
        assertNull(Dex.parseHeader("classes.dex", bytes))
    }

    @Test
    fun rejects_big_endian_and_too_short() {
        assertNull(Dex.parseHeader("classes.dex", dexHeader(endian = 0x78563412L)))
        assertNull(Dex.parseHeader("classes.dex", ByteArray(16)))
    }

    @Test
    fun flags_dex_that_nears_the_method_limit() {
        val hot = Dex.parseHeader("classes.dex", dexHeader(methodIds = 61_000))!!
        val cool = Dex.parseHeader("classes2.dex", dexHeader(methodIds = 100))!!
        assertTrue(hot.nearsMethodLimit)
        assertFalse(cool.nearsMethodLimit)
    }

    @Test
    fun aggregate_sums_across_files() {
        val a = Dex.parseHeader("classes.dex", dexHeader(methodIds = 100, classDefs = 10, fieldIds = 20, stringIds = 30))!!
        val b = Dex.parseHeader("classes2.dex", dexHeader(methodIds = 61_000, classDefs = 5, fieldIds = 2, stringIds = 3))!!
        val stats = DexStats(listOf(a, b))
        assertEquals(2, stats.dexCount)
        assertEquals(61_100, stats.totalMethods)
        assertEquals(15, stats.totalClasses)
        assertEquals(22, stats.totalFields)
        assertEquals(33, stats.totalStrings)
        assertTrue(stats.nearsMethodLimit)
    }
}
