package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ApkCompositionTest {

    private fun entry(name: String, size: Long, dir: Boolean = false) =
        ArchiveEntry(name, size, size, dir, CompressionMethod.Deflated)

    @Test
    fun groups_entries_by_part_and_sums_bytes() {
        val entries = listOf(
            entry("classes.dex", 100),
            entry("classes2.dex", 50),
            entry("resources.arsc", 200),
            entry("res/drawable/a.png", 30),
            entry("res/layout/main.xml", 10),
            entry("lib/arm64-v8a/libx.so", 300),
            entry("assets/data.json", 5),
            entry("META-INF/CERT.RSA", 2),
            entry("unknown.bin", 1),
        )

        val result = apkComposition(entries).associate { it.part to it.bytes }

        assertEquals(150L, result[ApkPart.Dex])
        assertEquals(200L, result[ApkPart.ResourceTable])
        assertEquals(40L, result[ApkPart.Resources])
        assertEquals(300L, result[ApkPart.Native])
        assertEquals(5L, result[ApkPart.Assets])
        assertEquals(2L, result[ApkPart.Signatures])
        assertEquals(1L, result[ApkPart.Other])
    }

    @Test
    fun sorted_largest_first_and_drops_directories_and_zero() {
        val entries = listOf(
            entry("res/", 0, dir = true),
            entry("lib/arm64-v8a/libbig.so", 500),
            entry("classes.dex", 100),
        )
        val parts = apkComposition(entries)
        assertEquals(listOf(ApkPart.Native, ApkPart.Dex), parts.map { it.part })
    }
}
