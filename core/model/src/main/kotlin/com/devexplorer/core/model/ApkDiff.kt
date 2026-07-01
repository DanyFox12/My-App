package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * A READ-ONLY structural comparison of two [ApkSummary] snapshots — "what
 * changed between these two APKs?". Pure and deterministic (no Android, no I/O),
 * so it is unit-tested here and reused verbatim by the Compare screen and its
 * shareable report.
 *
 * By convention the first argument is the old (baseline) APK and the second is
 * the new (candidate); deltas are `new - old`.
 */
@Serializable
data class ApkDiff(
    val identity: List<FieldChange>,
    val sizes: SizeDelta,
    val permissionsAdded: List<String>,
    val permissionsRemoved: List<String>,
    val permissionsCommon: Int,
    val composition: List<CompositionDelta>,
    val dex: DexDelta,
    val signing: SigningComparison,
) {
    /** True when nothing meaningful differs between the two archives. */
    val isIdentical: Boolean
        get() = identity.none { it.changed } &&
            permissionsAdded.isEmpty() && permissionsRemoved.isEmpty() &&
            sizes.uncompressedDelta == 0L && composition.all { it.delta == 0L } &&
            dex.methodsDelta == 0 && signing != SigningComparison.Different
}

/** One before/after field (package, version, SDK level, …). */
@Serializable
data class FieldChange(
    val label: String,
    val oldValue: String?,
    val newValue: String?,
) {
    val changed: Boolean get() = oldValue != newValue
}

/** Archive-size deltas. Positive means the new APK is larger. */
@Serializable
data class SizeDelta(
    val oldUncompressed: Long,
    val newUncompressed: Long,
    val oldCompressed: Long,
    val newCompressed: Long,
    val oldEntries: Int,
    val newEntries: Int,
) {
    val uncompressedDelta: Long get() = newUncompressed - oldUncompressed
    val compressedDelta: Long get() = newCompressed - oldCompressed
    val entriesDelta: Int get() = newEntries - oldEntries
}

/** Per-[ApkPart] size change; the list is sorted largest-magnitude-first. */
@Serializable
data class CompositionDelta(
    val part: ApkPart,
    val oldBytes: Long,
    val newBytes: Long,
) {
    val delta: Long get() = newBytes - oldBytes
}

/** DEX-level deltas (method/class/field references and DEX-file count). */
@Serializable
data class DexDelta(
    val oldMethods: Int,
    val newMethods: Int,
    val oldClasses: Int,
    val newClasses: Int,
    val oldDexFiles: Int,
    val newDexFiles: Int,
) {
    val methodsDelta: Int get() = newMethods - oldMethods
    val classesDelta: Int get() = newClasses - oldClasses
    val dexFilesDelta: Int get() = newDexFiles - oldDexFiles
}

/** Whether the two APKs are signed by the same certificate set. */
@Serializable
enum class SigningComparison {
    /** Both signed and their certificate SHA-256 sets match. */
    Same,

    /** Both signed but the certificate sets differ (a re-signed / different build). */
    Different,

    /** At least one side has no signing information to compare. */
    Unknown,
}

/**
 * Compute the [ApkDiff] between [old] and [new]. Deltas are `new - old`.
 */
fun diffApks(old: ApkSummary, new: ApkSummary): ApkDiff {
    val identity = listOf(
        FieldChange("Package", old.packageName, new.packageName),
        FieldChange("Label", old.appLabel, new.appLabel),
        FieldChange("Version name", old.versionName, new.versionName),
        FieldChange("Version code", old.versionCode?.toString(), new.versionCode?.toString()),
        FieldChange("Min SDK", old.minSdk?.toString(), new.minSdk?.toString()),
        FieldChange("Target SDK", old.targetSdk?.toString(), new.targetSdk?.toString()),
        FieldChange("Compile SDK", old.compileSdk?.toString(), new.compileSdk?.toString()),
    )

    val oldPerms = old.permissions.toSet()
    val newPerms = new.permissions.toSet()

    val oldComp = apkComposition(old.entries).associate { it.part to it.bytes }
    val newComp = apkComposition(new.entries).associate { it.part to it.bytes }
    val composition = (oldComp.keys + newComp.keys)
        .map { part -> CompositionDelta(part, oldComp[part] ?: 0L, newComp[part] ?: 0L) }
        .sortedByDescending { kotlin.math.abs(it.delta) }

    val dex = DexDelta(
        oldMethods = old.dexStats?.totalMethods ?: 0,
        newMethods = new.dexStats?.totalMethods ?: 0,
        oldClasses = old.dexStats?.totalClasses ?: 0,
        newClasses = new.dexStats?.totalClasses ?: 0,
        oldDexFiles = old.dexStats?.dexCount ?: old.dexCount,
        newDexFiles = new.dexStats?.dexCount ?: new.dexCount,
    )

    return ApkDiff(
        identity = identity,
        sizes = SizeDelta(
            oldUncompressed = old.totalUncompressedBytes,
            newUncompressed = new.totalUncompressedBytes,
            oldCompressed = old.totalCompressedBytes,
            newCompressed = new.totalCompressedBytes,
            oldEntries = old.entryCount,
            newEntries = new.entryCount,
        ),
        permissionsAdded = (newPerms - oldPerms).sorted(),
        permissionsRemoved = (oldPerms - newPerms).sorted(),
        permissionsCommon = (oldPerms intersect newPerms).size,
        composition = composition,
        dex = dex,
        signing = compareSigning(old.signingInfo, new.signingInfo),
    )
}

private fun compareSigning(old: SigningInfo?, new: SigningInfo?): SigningComparison {
    if (old == null || new == null) return SigningComparison.Unknown
    val oldCerts = old.certificates.map { it.sha256 }.toSet()
    val newCerts = new.certificates.map { it.sha256 }.toSet()
    if (oldCerts.isEmpty() || newCerts.isEmpty()) return SigningComparison.Unknown
    return if (oldCerts == newCerts) SigningComparison.Same else SigningComparison.Different
}
