package com.devexplorer.core.model

/**
 * Renders a READ-ONLY [ApkSummary] as a plain-text report suitable for sharing
 * or exporting (e.g. via Intent.ACTION_SEND). Pure and deterministic — no
 * Android, no I/O — so it is unit-tested here and reused verbatim by the APK
 * Viewer's share action.
 *
 * The layout mirrors what the APK Viewer shows on screen: identity, archive
 * stats, size composition, permissions, and signing fingerprints.
 */
fun buildApkReport(summary: ApkSummary): String = buildString {
    fun row(label: String, value: String?) {
        append(label).append(": ").append(value ?: "—").append('\n')
    }

    appendLine("DevExplorer — APK report")
    appendLine("========================")
    appendLine()

    appendLine("Identity")
    row("Package", summary.packageName)
    row("Label", summary.appLabel)
    row("Version name", summary.versionName)
    row("Version code", summary.versionCode?.toString())
    row("Min SDK", summary.minSdk?.toString())
    row("Target SDK", summary.targetSdk?.toString())
    row("Compile SDK", summary.compileSdk?.toString())
    appendLine()

    appendLine("Archive")
    row("Entries", summary.entryCount.toString())
    row("DEX files", summary.dexCount.toString())
    row("Uncompressed bytes", summary.totalUncompressedBytes.toString())
    row("Compressed bytes", summary.totalCompressedBytes.toString())
    row("resources.arsc", if (summary.hasResourcesArsc) "present" else "absent")
    appendLine()

    val composition = apkComposition(summary.entries)
    if (composition.isNotEmpty()) {
        appendLine("Composition (uncompressed)")
        for (slice in composition) {
            append("  ").append(slice.part.label).append(": ")
                .append(slice.bytes.toString()).append(" bytes\n")
        }
        appendLine()
    }

    val dex = summary.dexStats
    if (dex != null && dex.files.isNotEmpty()) {
        appendLine("DEX (methods count toward the 65,536-per-file limit)")
        row("  Methods", dex.totalMethods.toString())
        row("  Classes", dex.totalClasses.toString())
        row("  Fields", dex.totalFields.toString())
        for (file in dex.files) {
            append("  ").append(file.name).append(": ")
                .append(file.methodIds.toString()).append(" methods, ")
                .append(file.classDefs.toString()).append(" classes")
            if (file.nearsMethodLimit) append("  [near 64K limit]")
            append('\n')
        }
        appendLine()
    }

    val nativeLibs = summary.nativeLibs
    if (nativeLibs != null && nativeLibs.files.isNotEmpty()) {
        appendLine("Native libraries")
        for (abi in nativeLibs.summaries) {
            append("  ").append(abi.abi).append(": ")
                .append(abi.fileCount.toString()).append(" files, ")
                .append(abi.totalBytes.toString()).append(" bytes")
            when (abi.supports16KbPages) {
                true -> append("  [16 KB ready]")
                false -> append("  [NOT 16 KB ready]")
                null -> Unit
            }
            append('\n')
        }
        appendLine()
    }

    val security = securityAudit(summary)
    if (security.isNotEmpty()) {
        appendLine("Security audit (${security.size})")
        for (finding in security) {
            append("  [").append(finding.severity.name.lowercase()).append("] ")
                .append(finding.check.reportLabel()).append('\n')
            for (item in finding.items) append("    - ").append(item).append('\n')
        }
        appendLine()
    }

    appendLine("Permissions (${summary.permissions.size})")
    if (summary.permissions.isEmpty()) {
        appendLine("  (none)")
    } else {
        for (permission in summary.permissions) append("  ").append(permission).append('\n')
    }
    appendLine()

    appendLine("Signing")
    val signing = summary.signingInfo
    if (signing == null) {
        appendLine("  No signing certificate found.")
    } else {
        row("  v1 (JAR) scheme", if (signing.schemeV1) "yes" else "no")
        row("  Multiple signers", if (signing.hasMultipleSigners) "yes" else "no")
        signing.certificates.forEachIndexed { index, cert ->
            appendLine("  Certificate ${index + 1} of ${signing.certificates.size}")
            append("    Subject: ").append(cert.subject).append('\n')
            append("    SHA-256: ").append(cert.sha256).append('\n')
            append("    SHA-1: ").append(cert.sha1).append('\n')
        }
    }
}.trimEnd('\n') + "\n"

/** Compact English labels for the plain-text report (the UI localizes its own). */
private fun SecurityCheck.reportLabel(): String = when (this) {
    SecurityCheck.Debuggable -> "debuggable build"
    SecurityCheck.TestOnly -> "test-only build"
    SecurityCheck.AllowBackup -> "backup allowed"
    SecurityCheck.CleartextTraffic -> "cleartext traffic allowed"
    SecurityCheck.ExportedComponents -> "exported components without a permission"
    SecurityCheck.ExportedProviders -> "exported content providers"
    SecurityCheck.DangerousPermissions -> "sensitive permissions requested"
    SecurityCheck.OutdatedTargetSdk -> "outdated target SDK"
    SecurityCheck.NoManifest -> "no manifest to audit"
}
