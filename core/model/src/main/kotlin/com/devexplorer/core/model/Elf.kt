package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * Parsed facts from one ELF binary's header (a `.so` entry under `lib/<abi>/`).
 *
 * [minLoadAlignment] is the smallest `p_align` across the PT_LOAD program
 * headers — the number that decides whether the library can be mapped on a
 * device with 16 KB memory pages (Android 15's new hardware wave). Null when
 * the program headers weren't inside the bytes we were given.
 */
@Serializable
data class ElfInfo(
    val is64Bit: Boolean,
    /** Human-readable machine, e.g. "ARM64" — see [Elf.machineName]. */
    val machine: String,
    val minLoadAlignment: Long?,
) {
    /** True/false when the PT_LOAD alignment is known, null when it isn't. */
    val supports16KbPages: Boolean? get() = minLoadAlignment?.let { it >= PAGE_16K }

    private companion object {
        const val PAGE_16K = 16_384L
    }
}

/**
 * A from-scratch reader for the **ELF header** and program-header table — no
 * NDK tooling. Everything we surface lives in the first few hundred bytes of
 * the file: the identity block (class/endianness), `e_machine`, and the
 * PT_LOAD alignments from the program headers (which sit right after the
 * header in every toolchain-produced `.so`).
 *
 * Deliberately **fail-soft**: anything that isn't a well-formed little-endian
 * ELF returns null instead of throwing (matching [BinaryXml] and [Dex]).
 */
object Elf {

    /** Bytes callers should read from the start of a `.so` to cover the program headers. */
    const val HEADER_READ_BYTES: Int = 4096

    private const val PT_LOAD = 1L

    // e_machine values for the ABIs Android ships (plus RISC-V, the next one).
    private const val EM_386 = 3
    private const val EM_ARM = 40
    private const val EM_X86_64 = 62
    private const val EM_AARCH64 = 183
    private const val EM_RISCV = 243

    fun parse(bytes: ByteArray): ElfInfo? = runCatching { parseOrThrow(bytes) }.getOrNull()

    private fun parseOrThrow(bytes: ByteArray): ElfInfo? {
        // e_ident: 0x7F "ELF", then class (1=32-bit, 2=64-bit) and data (1=LE).
        if (bytes.size < 0x40) return null
        if (u8(bytes, 0) != 0x7F || bytes[1].toInt() != 'E'.code ||
            bytes[2].toInt() != 'L'.code || bytes[3].toInt() != 'F'.code
        ) {
            return null
        }
        val is64 = when (u8(bytes, 4)) {
            1 -> false
            2 -> true
            else -> return null
        }
        if (u8(bytes, 5) != 1) return null // big-endian ELF: not an Android ABI

        val machine = machineName(u16(bytes, 18))
        return ElfInfo(
            is64Bit = is64,
            machine = machine,
            minLoadAlignment = readMinLoadAlignment(bytes, is64),
        )
    }

    /**
     * Walk the program-header table and return the smallest PT_LOAD `p_align`,
     * or null when the table lies beyond [bytes] or holds no PT_LOAD entry.
     */
    private fun readMinLoadAlignment(bytes: ByteArray, is64: Boolean): Long? {
        val phOff: Long
        val phEntSize: Int
        val phNum: Int
        if (is64) {
            phOff = u64(bytes, 0x20)
            phEntSize = u16(bytes, 0x36)
            phNum = u16(bytes, 0x38)
        } else {
            phOff = u32(bytes, 0x1C)
            phEntSize = u16(bytes, 0x2A)
            phNum = u16(bytes, 0x2C)
        }
        if (phNum == 0 || phEntSize <= 0) return null
        val tableEnd = phOff + phNum.toLong() * phEntSize
        if (phOff <= 0 || tableEnd > bytes.size) return null

        var min: Long? = null
        for (i in 0 until phNum) {
            val p = (phOff + i.toLong() * phEntSize).toInt()
            val type = u32(bytes, p)
            if (type != PT_LOAD) continue
            val align = if (is64) u64(bytes, p + 0x30) else u32(bytes, p + 0x1C)
            min = if (min == null) align else minOf(min, align)
        }
        return min
    }

    internal fun machineName(machine: Int): String = when (machine) {
        EM_386 -> "x86"
        EM_ARM -> "ARM"
        EM_X86_64 -> "x86-64"
        EM_AARCH64 -> "ARM64"
        EM_RISCV -> "RISC-V"
        else -> "0x%x".format(machine)
    }

    private fun u8(b: ByteArray, p: Int): Int = b[p].toInt() and 0xFF
    private fun u16(b: ByteArray, p: Int): Int = u8(b, p) or (u8(b, p + 1) shl 8)
    private fun u32(b: ByteArray, p: Int): Long =
        u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    private fun u64(b: ByteArray, p: Int): Long = u32(b, p) or (u32(b, p + 4) shl 32)
}
