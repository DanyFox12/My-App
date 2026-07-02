package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLibsTest {

    // A header-only ELF (no program headers): parses, alignment unknown.
    private fun elfBytes(is64: Boolean): ByteArray {
        val b = ByteArray(0x40)
        b[0] = 0x7F
        b[1] = 'E'.code.toByte()
        b[2] = 'L'.code.toByte()
        b[3] = 'F'.code.toByte()
        b[4] = if (is64) 2 else 1
        b[5] = 1
        b[18] = if (is64) 183.toByte() else 40
        return b
    }

    @Test
    fun groups_by_abi_and_sums_sizes() {
        val libs = NativeLibs.build(
            listOf(
                Triple("lib/arm64-v8a/liba.so", 1000L, elfBytes(is64 = true)),
                Triple("lib/arm64-v8a/libb.so", 500L, elfBytes(is64 = true)),
                Triple("lib/armeabi-v7a/liba.so", 300L, elfBytes(is64 = false)),
                Triple("assets/not-a-lib.so.txt", 99L, ByteArray(4)),
            ),
        )!!
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), libs.abis)
        assertEquals(1800L, libs.totalBytes)

        val arm64 = libs.summaries.first { it.abi == "arm64-v8a" }
        assertEquals(2, arm64.fileCount)
        assertEquals(1500L, arm64.totalBytes)
        // Alignment unknown (header-only ELF) -> fitness unknown.
        assertNull(arm64.supports16KbPages)

        assertEquals(listOf("libb.so", "liba.so").sorted(), libs.filesFor("arm64-v8a").map { it.fileName }.sorted())
        assertEquals("liba.so", libs.filesFor("arm64-v8a").first { it.sizeBytes == 1000L }.fileName)
    }

    @Test
    fun keeps_unparseable_entries_with_null_elf() {
        val libs = NativeLibs.build(
            listOf(Triple("lib/x86_64/broken.so", 42L, byteArrayOf(1, 2, 3))),
        )!!
        assertEquals(1, libs.files.size)
        assertNull(libs.files.single().elf)
        assertEquals("x86_64", libs.files.single().abi)
    }

    @Test
    fun returns_null_when_there_are_no_native_libs() {
        assertNull(NativeLibs.build(emptyList()))
        assertNull(NativeLibs.build(listOf(Triple("classes.dex", 10L, ByteArray(4)))))
    }

    @Test
    fun recognizes_native_lib_paths() {
        assertTrue(NativeLibs.isNativeLibPath("lib/arm64-v8a/libfoo.so"))
        assertTrue(!NativeLibs.isNativeLibPath("lib/arm64-v8a/readme.txt"))
        assertTrue(!NativeLibs.isNativeLibPath("assets/lib/fake.so.bak"))
    }
}
