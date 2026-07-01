package com.devexplorer.core.model

/** A high-level category of what lives inside an APK, for the size breakdown. */
enum class ApkPart(val label: String) {
    Dex("DEX code"),
    ResourceTable("Resource table"),
    Resources("Resources"),
    Native("Native libraries"),
    Assets("Assets"),
    Signatures("Signatures"),
    Other("Other"),
}

/** One slice of the composition: a part and its total uncompressed bytes. */
data class ApkCompositionSlice(
    val part: ApkPart,
    val bytes: Long,
)

/**
 * Groups an archive's entries into [ApkPart] buckets by uncompressed size — the
 * data behind the "X-ray" treemap. Pure and deterministic, so it's unit-tested.
 * Slices with zero bytes are dropped; the result is sorted largest-first.
 */
fun apkComposition(entries: List<ArchiveEntry>): List<ApkCompositionSlice> {
    val totals = linkedMapOf<ApkPart, Long>()
    for (entry in entries) {
        if (entry.isDirectory) continue
        val part = classifyPart(entry.name)
        totals[part] = (totals[part] ?: 0L) + entry.sizeBytes
    }
    return totals.map { ApkCompositionSlice(it.key, it.value) }
        .filter { it.bytes > 0 }
        .sortedByDescending { it.bytes }
}

private fun classifyPart(name: String): ApkPart = when {
    name.endsWith(".dex") -> ApkPart.Dex
    name == "resources.arsc" -> ApkPart.ResourceTable
    name.startsWith("res/") -> ApkPart.Resources
    name.startsWith("lib/") -> ApkPart.Native
    name.startsWith("assets/") -> ApkPart.Assets
    name.startsWith("META-INF/") -> ApkPart.Signatures
    else -> ApkPart.Other
}
