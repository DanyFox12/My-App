package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/**
 * A framework-free handle to a storage location.
 *
 * We deliberately do NOT use [android.net.Uri] or [java.io.File] in the domain
 * layer. Those are Android/IO types; letting them flow through the app would
 * couple every feature to the platform and make the domain untestable on a
 * plain JVM. Instead the data layer translates between the real `Uri`/`File`
 * and this value type at its boundary.
 *
 * The [raw] string is whatever the backing source needs to re-resolve the
 * location (a SAF tree/document Uri string, a package name, or a sandbox-
 * relative path). [kind] tells consumers how to interpret it.
 */
@Serializable
data class StorageRef(
    val raw: String,
    val kind: Kind,
) {
    enum class Kind {
        /** A Storage Access Framework document or tree Uri (read-only sources). */
        SafDocument,
        SafTree,

        /** An installed package, addressed by package name. */
        InstalledPackage,

        /** A path inside the app-private Workspace sandbox (the only writable zone). */
        WorkspacePath,
    }

    companion object {
        fun safTree(uri: String) = StorageRef(uri, Kind.SafTree)
        fun safDocument(uri: String) = StorageRef(uri, Kind.SafDocument)
        fun installedPackage(packageName: String) =
            StorageRef(packageName, Kind.InstalledPackage)
        fun workspace(relativePath: String) =
            StorageRef(relativePath, Kind.WorkspacePath)
    }
}
