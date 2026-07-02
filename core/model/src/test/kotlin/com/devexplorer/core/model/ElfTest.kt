package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElfTest {

    /** Build a minimal little-endian ELF with the given PT_LOAD alignments. */
    private fun elf(
        is64: Boolean = true,
        machine: Int = 183, // ARM64
        loadAlignments: List<Long> = listOf(16_384L),
        littleEndian: Boolean = true,
    ): ByteArray {
        val phEntSize = if (is64) 56 else 32
        val phOff = 0x40
        val b = ByteArray(phOff + loadAlignments.size * phEntSize)
        b[0] = 0x7F
        b[1] = 'E'.code.toByte()
        b[2] = 'L'.code.toByte()
        b[3] = 'F'.code.toByte()
        b[4] = if (is64) 2 else 1
        b[5] = if (littleEndian) 1 else 2

        fun putU16(off: Int, v: Int) {
            b[off] = (v and 0xFF).toByte()
            b[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        fun putU32(off: Int, v: Long) {
            putU16(off, (v and 0xFFFF).toInt())
            putU16(off + 2, ((v shr 16) and 0xFFFF).toInt())
        }
        fun putU64(off: Int, v: Long) {
            putU32(off, v and 0xFFFFFFFFL)
            putU32(off + 4, (v ushr 32))
        }

        putU16(18, machine)
        if (is64) {
            putU64(0x20, phOff.toLong())
            putU16(0x36, phEntSize)
            putU16(0x38, loadAlignments.size)
        } else {
            putU32(0x1C, phOff.toLong())
            putU16(0x2A, phEntSize)
            putU16(0x2C, loadAlignments.size)
        }
        loadAlignments.forEachIndexed { i, align ->
            val p = phOff + i * phEntSize
            putU32(p, 1L) // PT_LOAD
            if (is64) putU64(p + 0x30, align) else putU32(p + 0x1C, align)
        }
        return b
    }

    @Test
    fun parses_arm64_with_16k_alignment() {
        val info = Elf.parse(elf(is64 = true, machine = 183, loadAlignments = listOf(16_384L, 65_536L)))!!
        assertEquals(true, info.is64Bit)
        assertEquals("ARM64", info.machine)
        assertEquals(16_384L, info.minLoadAlignment)
        assertEquals(true, info.supports16KbPages)
    }

    @Test
    fun flags_4k_only_libraries() {
        val info = Elf.parse(elf(loadAlignments = listOf(4_096L, 16_384L)))!!
        assertEquals(4_096L, info.minLoadAlignment)
        assertEquals(false, info.supports16KbPages)
    }

    @Test
    fun parses_32bit_arm() {
        val info = Elf.parse(elf(is64 = false, machine = 40, loadAlignments = listOf(4_096L)))!!
        assertEquals(false, info.is64Bit)
        assertEquals("ARM", info.machine)
        assertEquals(4_096L, info.minLoadAlignment)
    }

    @Test
    fun alignment_is_unknown_when_program_headers_are_cut_off() {
        val whole = elf(loadAlignments = listOf(16_384L))
        val truncated = whole.copyOf(0x40) // header only, no program headers
        val info = Elf.parse(truncated)!!
        assertNull(info.minLoadAlignment)
        assertNull(info.supports16KbPages)
    }

    @Test
    fun rejects_non_elf_and_big_endian() {
        assertNull(Elf.parse(ByteArray(0x40)))
        assertNull(Elf.parse("not an elf at all - just some text padding!!!!!!!!!!!!!!!!!".toByteArray()))
        assertNull(Elf.parse(elf(littleEndian = false)))
    }

    @Test
    fun names_unknown_machines_in_hex() {
        assertEquals("0xf00", Elf.machineName(0xF00))
        assertEquals("x86-64", Elf.machineName(62))
        assertEquals("RISC-V", Elf.machineName(243))
    }
}
