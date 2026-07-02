package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * One observation from the read-only security audit of an APK. [check] is the
 * machine-readable identity (the UI maps it to localized text); [items] is the
 * concrete evidence — component names, permission names — backing the finding.
 */
@Serializable
data class SecurityFinding(
    val check: SecurityCheck,
    val severity: SecuritySeverity,
    val items: List<String> = emptyList(),
)

enum class SecuritySeverity { Info, Note, Warning }

enum class SecurityCheck {
    /** android:debuggable="true" — anyone with adb can run-as this app. */
    Debuggable,

    /** android:testOnly="true" — not installable through normal channels. */
    TestOnly,

    /** android:allowBackup="true" — app data is extractable via backup. */
    AllowBackup,

    /** android:usesCleartextTraffic="true" — plain-HTTP traffic permitted. */
    CleartextTraffic,

    /** Exported activities/services/receivers without a guarding permission. */
    ExportedComponents,

    /** Exported content providers without a guarding permission (data exposure). */
    ExportedProviders,

    /** Requested permissions from the dangerous/user-sensitive bucket. */
    DangerousPermissions,

    /** targetSdkVersion is old enough to opt out of modern platform protections. */
    OutdatedTargetSdk,

    /** No decodable manifest — the audit could not run. */
    NoManifest,
}

/**
 * Read-only security audit over an already-parsed [ApkSummary]: everything is
 * derived from the decoded binary manifest and the requested-permission list —
 * no code is executed, nothing is touched on disk. Pure and deterministic, so
 * it's unit-tested here; the sorting puts the loudest findings first.
 */
fun securityAudit(summary: ApkSummary): List<SecurityFinding> {
    val manifest = summary.manifest
        ?: return listOf(SecurityFinding(SecurityCheck.NoManifest, SecuritySeverity.Info))

    val findings = mutableListOf<SecurityFinding>()
    val application = manifest.children.firstOrNull { it.name == "application" }

    fun XmlNode.attr(name: String): String? = attributes.firstOrNull { it.name == name }?.value
    fun XmlNode.flag(name: String): Boolean = attr(name) == "true"

    if (application?.flag("android:debuggable") == true) {
        findings += SecurityFinding(SecurityCheck.Debuggable, SecuritySeverity.Warning)
    }
    if (application?.flag("android:testOnly") == true) {
        findings += SecurityFinding(SecurityCheck.TestOnly, SecuritySeverity.Note)
    }
    if (application?.flag("android:allowBackup") == true) {
        findings += SecurityFinding(SecurityCheck.AllowBackup, SecuritySeverity.Note)
    }
    if (application?.flag("android:usesCleartextTraffic") == true) {
        findings += SecurityFinding(SecurityCheck.CleartextTraffic, SecuritySeverity.Warning)
    }

    if (application != null) {
        // Before API 31 made android:exported mandatory, a component with an
        // <intent-filter> and no explicit attribute was exported by default —
        // historically the most common unguarded-export vulnerability.
        val implicitExport = (summary.targetSdk ?: 0) < EXPLICIT_EXPORT_SDK
        fun XmlNode.isExported(): Boolean = when (attr("android:exported")) {
            "true" -> true
            "false" -> false
            else -> implicitExport && children.any { it.name == "intent-filter" }
        }

        fun exportedUnguarded(kinds: Set<String>): List<String> = application.children
            .filter { it.name in kinds && it.isExported() && it.attr("android:permission") == null }
            .mapNotNull { it.attr("android:name") }

        val components = exportedUnguarded(setOf("activity", "activity-alias", "service", "receiver"))
        if (components.isNotEmpty()) {
            findings += SecurityFinding(SecurityCheck.ExportedComponents, SecuritySeverity.Note, components)
        }
        val providers = exportedUnguarded(setOf("provider"))
        if (providers.isNotEmpty()) {
            findings += SecurityFinding(SecurityCheck.ExportedProviders, SecuritySeverity.Warning, providers)
        }
    }

    val requested = summary.permissions.ifEmpty {
        manifest.children
            .filter { it.name == "uses-permission" }
            .mapNotNull { it.attr("android:name") }
    }
    val dangerous = requested.filter { it in DANGEROUS_PERMISSIONS }.sorted()
    if (dangerous.isNotEmpty()) {
        findings += SecurityFinding(SecurityCheck.DangerousPermissions, SecuritySeverity.Info, dangerous)
    }

    val targetSdk = summary.targetSdk
    if (targetSdk != null && targetSdk < MODERN_TARGET_SDK) {
        val severity =
            if (targetSdk < LEGACY_TARGET_SDK) SecuritySeverity.Warning else SecuritySeverity.Note
        findings += SecurityFinding(SecurityCheck.OutdatedTargetSdk, severity, listOf(targetSdk.toString()))
    }

    return findings.sortedWith(
        compareByDescending<SecurityFinding> { it.severity.ordinal }.thenBy { it.check.ordinal },
    )
}

/** From this target SDK the platform requires android:exported to be explicit. */
private const val EXPLICIT_EXPORT_SDK = 31

/** Below this target SDK the app opts out of runtime-permission-era protections. */
private const val LEGACY_TARGET_SDK = 23

/** Below this target SDK the app opts out of scoped storage / component-export rules. */
private const val MODERN_TARGET_SDK = 30

/**
 * Well-known permissions from the platform's dangerous/user-sensitive bucket.
 * A curated constant (not PackageManager lookups) so the audit stays pure and
 * works for APKs that aren't installed.
 */
private val DANGEROUS_PERMISSIONS: Set<String> = setOf(
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACTIVITY_RECOGNITION",
    "android.permission.ANSWER_PHONE_CALLS",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BODY_SENSORS",
    "android.permission.CALL_PHONE",
    "android.permission.CAMERA",
    "android.permission.GET_ACCOUNTS",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.NEARBY_WIFI_DEVICES",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.PROCESS_OUTGOING_CALLS",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.READ_CALENDAR",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_MMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.RECEIVE_WAP_PUSH",
    "android.permission.RECORD_AUDIO",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SEND_SMS",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.USE_SIP",
    "android.permission.WRITE_CALENDAR",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.WRITE_CONTACTS",
    "android.permission.WRITE_EXTERNAL_STORAGE",
)
