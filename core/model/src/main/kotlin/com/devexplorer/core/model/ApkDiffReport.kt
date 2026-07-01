package com.devexplorer.core.model

/**
 * Renders an [ApkDiff] as a plain-text report suitable for sharing or exporting
 * (e.g. via `Intent.ACTION_SEND`). Pure and deterministic — no Android, no I/O —
 * so it is unit-tested here and reused verbatim by the Compare screen's share
 * action, mirroring [buildApkReport].
 *
 * [oldName] / [newName] are display labels for the two sides (a file name or a
 * package name); they affect only the header.
 */
fun buildApkDiffReport(
    diff: ApkDiff,
    oldName: String = "old",
    newName: String = "new",
): String = buildString {
    fun signed(value: Long): String = if (value >= 0) "+$value" else value.toString()
    fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

    appendLine("DevExplorer — APK comparison")
    appendLine("===========================")
    appendLine("old: $oldName")
    appendLine("new: $newName")
    appendLine()

    if (diff.isIdentical) {
        appendLine("No meaningful differences found.")
        return@buildString
    }

    appendLine("Identity")
    for (change in diff.identity) {
        val marker = if (change.changed) "~" else " "
        append(marker).append(' ').append(change.label).append(": ")
        if (change.changed) {
            append(change.oldValue ?: "—").append(" -> ").append(change.newValue ?: "—")
        } else {
            append(change.oldValue ?: "—")
        }
        append('\n')
    }
    appendLine()

    appendLine("Size")
    with(diff.sizes) {
        appendLine("  Uncompressed: $oldUncompressed -> $newUncompressed (${signed(uncompressedDelta)})")
        appendLine("  Compressed: $oldCompressed -> $newCompressed (${signed(compressedDelta)})")
        appendLine("  Entries: $oldEntries -> $newEntries (${signed(entriesDelta)})")
    }
    appendLine()

    val changedComposition = diff.composition.filter { it.delta != 0L }
    if (changedComposition.isNotEmpty()) {
        appendLine("Composition changes (uncompressed bytes)")
        for (slice in changedComposition) {
            appendLine("  ${slice.part.label}: ${slice.oldBytes} -> ${slice.newBytes} (${signed(slice.delta)})")
        }
        appendLine()
    }

    appendLine("DEX")
    with(diff.dex) {
        appendLine("  Methods: $oldMethods -> $newMethods (${signed(methodsDelta)})")
        appendLine("  Classes: $oldClasses -> $newClasses (${signed(classesDelta)})")
        appendLine("  DEX files: $oldDexFiles -> $newDexFiles (${signed(dexFilesDelta)})")
    }
    appendLine()

    appendLine("Permissions")
    appendLine("  Added (${diff.permissionsAdded.size}):")
    if (diff.permissionsAdded.isEmpty()) {
        appendLine("    (none)")
    } else {
        for (permission in diff.permissionsAdded) appendLine("    + $permission")
    }
    appendLine("  Removed (${diff.permissionsRemoved.size}):")
    if (diff.permissionsRemoved.isEmpty()) {
        appendLine("    (none)")
    } else {
        for (permission in diff.permissionsRemoved) appendLine("    - $permission")
    }
    appendLine("  Unchanged: ${diff.permissionsCommon}")
    appendLine()

    appendLine("Signing")
    appendLine(
        when (diff.signing) {
            SigningComparison.Same -> "  Same certificate set."
            SigningComparison.Different -> "  DIFFERENT certificate set — re-signed or a different author."
            SigningComparison.Unknown -> "  Not comparable (one or both APKs unsigned / unreadable)."
        },
    )
}.trimEnd('\n') + "\n"
