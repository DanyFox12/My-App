package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DexPackagesTest {

    /**
     * Builds a tiny but structurally valid DEX: header, string_ids, type_ids,
     * method_ids, class_defs and MUTF-8 string data, all correctly offset.
     */
    private fun dex(
        classDescriptors: List<String>,
        methodOwners: List<Int> = emptyList(), // type indices, one per method_id
    ): ByteArray {
        val strings = classDescriptors
        val stringData = ArrayList<Byte>()
        val stringOffsets = IntArray(strings.size)

        val headerSize = 0x70
        val stringIdsOff = headerSize
        val typeIdsOff = stringIdsOff + strings.size * 4
        val methodIdsOff = typeIdsOff + strings.size * 4
        val classDefsOff = methodIdsOff + methodOwners.size * 8
        val dataOff = classDefsOff + classDescriptors.size * 32

        strings.forEachIndexed { i, s ->
            stringOffsets[i] = dataOff + stringData.size
            stringData.add(s.length.toByte()) // ULEB128 utf16 length (< 128)
            stringData.addAll(s.toByteArray(Charsets.UTF_8).toList())
            stringData.add(0)
        }

        val total = dataOff + stringData.size
        val b = ByteArray(total)
        // magic + endian
        "dex\n035".toByteArray(Charsets.US_ASCII).copyInto(b, 0)
        b[7] = 0
        fun putU16(off: Int, v: Int) {
            b[off] = (v and 0xFF).toByte()
            b[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        fun putU32(off: Int, v: Int) { putU16(off, v and 0xFFFF); putU16(off + 2, (v ushr 16) and 0xFFFF) }
        putU32(0x28, 0x12345678)

        putU32(0x38, strings.size); putU32(0x3C, stringIdsOff)
        putU32(0x40, strings.size); putU32(0x44, typeIdsOff)
        putU32(0x58, methodOwners.size); putU32(0x5C, methodIdsOff)
        putU32(0x60, classDescriptors.size); putU32(0x64, classDefsOff)

        strings.indices.forEach { i -> putU32(stringIdsOff + i * 4, stringOffsets[i]) }
        strings.indices.forEach { i -> putU32(typeIdsOff + i * 4, i) } // type i -> string i
        methodOwners.forEachIndexed { i, owner -> putU16(methodIdsOff + i * 8, owner) }
        classDescriptors.indices.forEach { i -> putU32(classDefsOff + i * 32, i) } // class i -> type i
        stringData.forEachIndexed { i, byte -> b[dataOff + i] = byte }
        return b
    }

    @Test
    fun mid_parse_failure_leaves_no_partial_counts_behind() {
        val good = dex(classDescriptors = listOf("Lcom/good/Only;"), methodOwners = listOf(0))
        // Valid header, but class_defs_size claims far more entries than the
        // buffer holds: the class_defs loop counts a package, then reads out of
        // bounds and throws. The whole file must be skipped, counts intact.
        val bad = dex(classDescriptors = listOf("Lcom/bad/Polluter;"))
        run { // patch class_defs_size (offset 0x60) to a lying value
            val v = 1_000_000
            bad[0x60] = (v and 0xFF).toByte()
            bad[0x61] = ((v shr 8) and 0xFF).toByte()
            bad[0x62] = ((v shr 16) and 0xFF).toByte()
            bad[0x63] = ((v shr 24) and 0xFF).toByte()
        }

        val root = DexPackages.parse(listOf(good, bad))!!

        assertEquals(1, root.totalClasses)
        val com = root.children.single { it.name == "com" }
        assertEquals(listOf("good"), com.children.map { it.name })
    }

    @Test
    fun builds_a_package_tree_with_class_and_method_counts() {
        val bytes = dex(
            classDescriptors = listOf(
                "Lcom/example/app/Main;",
                "Lcom/example/app/Util;",
                "Lcom/example/net/Http;",
            ),
            methodOwners = listOf(0, 0, 1, 2, 2, 2),
        )
        val root = DexPackages.parse(listOf(bytes))!!

        assertEquals(3, root.totalClasses)
        assertEquals(6, root.totalMethodRefs)

        val com = root.children.single { it.name == "com" }
        val example = com.children.single { it.name == "example" }
        val app = example.children.single { it.name == "app" }
        val net = example.children.single { it.name == "net" }
        assertEquals(2, app.classCount)
        assertEquals(3, app.methodRefs)
        assertEquals(1, net.classCount)
        assertEquals(3, net.methodRefs)
        // Children are sorted by heaviest method usage first.
        assertEquals(example.children.map { it.totalMethodRefs }.sortedDescending(), example.children.map { it.totalMethodRefs })
    }

    @Test
    fun merges_multiple_dex_files() {
        val a = dex(listOf("Lcom/example/A;"), methodOwners = listOf(0))
        val b = dex(listOf("Lcom/example/B;"), methodOwners = listOf(0, 0))
        val root = DexPackages.parse(listOf(a, b))!!
        assertEquals(2, root.totalClasses)
        assertEquals(3, root.totalMethodRefs)
    }

    @Test
    fun skips_malformed_dex_but_keeps_good_ones() {
        val good = dex(listOf("Lcom/example/A;"))
        val root = DexPackages.parse(listOf(ByteArray(10), good))!!
        assertEquals(1, root.totalClasses)
        assertNull(DexPackages.parse(listOf(ByteArray(10))))
    }

    @Test
    fun maps_descriptors_to_packages() {
        assertEquals("com.example", DexPackages.descriptorToPackage("Lcom/example/Foo;"))
        assertEquals("", DexPackages.descriptorToPackage("LTopLevel;"))
        assertNull(DexPackages.descriptorToPackage("[Lcom/example/Foo;"))
        assertNull(DexPackages.descriptorToPackage("I"))
    }
}
