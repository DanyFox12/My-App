package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/** One native library inside the archive (`lib/<abi>/<name>.so`). */
@Serializable
data class NativeLibFile(
    /** Full archive path, e.g. "lib/arm64-v8a/libfoo.so". */
    val path: String,
    val sizeBytes: Long,
    /** Parsed ELF facts, or null when the entry wasn't a readable little-endian ELF. */
    val elf: ElfInfo?,
) {
    val abi: String get() = path.removePrefix("lib/").substringBefore('/')
    val fileName: String get() = path.substringAfterLast('/')
}

/** Per-ABI rollup of the native libraries. */
@Serializable
data class NativeAbiSummary(
    val abi: String,
    val fileCount: Int,
    val totalBytes: Long,
    /** True/false when every lib's 16 KB-page fitness is known, null when any is unknown. */
    val supports16KbPages: Boolean?,
)

/**
 * Aggregated view of everything under `lib/` in an archive. Pure and
 * serializable so it rides along inside [ApkSummary] and is unit-tested here;
 * the data layer only supplies (path, size, header bytes) per entry.
 */
@Serializable
data class NativeLibs(
    val files: List<NativeLibFile>,
) {
    val abis: List<String> get() = files.map { it.abi }.distinct().sorted()

    val totalBytes: Long get() = files.sumOf { it.sizeBytes }

    val summaries: List<NativeAbiSummary>
        get() = files.groupBy { it.abi }.map { (abi, libs) ->
            val fitness = libs.map { it.elf?.supports16KbPages }
            NativeAbiSummary(
                abi = abi,
                fileCount = libs.size,
                totalBytes = libs.sumOf { it.sizeBytes },
                supports16KbPages = when {
                    fitness.any { it == null } -> null
                    else -> fitness.all { it == true }
                },
            )
        }.sortedBy { it.abi }

    fun filesFor(abi: String): List<NativeLibFile> =
        files.filter { it.abi == abi }.sortedByDescending { it.sizeBytes }

    companion object {
        /** True for entries that should be parsed as native libraries. */
        fun isNativeLibPath(name: String): Boolean =
            name.startsWith("lib/") && name.endsWith(".so")

        /**
         * Build the aggregate from (archive path, uncompressed size, first bytes)
         * triples. Entries whose bytes don't parse still appear (with null [ElfInfo])
         * so the per-ABI sizes stay truthful.
         */
        fun build(entries: List<Triple<String, Long, ByteArray>>): NativeLibs? {
            val files = entries
                .filter { (path, _, _) -> isNativeLibPath(path) }
                .map { (path, size, bytes) -> NativeLibFile(path, size, Elf.parse(bytes)) }
                .sortedBy { it.path }
            return if (files.isEmpty()) null else NativeLibs(files)
        }
    }
}
