package com.devexplorer.app.navigation

import androidx.compose.material.icons.filled.Apps as FilledApps
import androidx.compose.material.icons.filled.Build as FilledBuild
import androidx.compose.material.icons.filled.Folder as FilledFolder
import androidx.compose.material.icons.filled.Settings as FilledSettings
import androidx.compose.material.icons.outlined.Apps as OutlinedApps
import androidx.compose.material.icons.outlined.Build as OutlinedBuild
import androidx.compose.material.icons.outlined.Folder as OutlinedFolder
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.ui.graphics.vector.ImageVector
import com.devexplorer.app.R
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation-Compose + kotlinx.serialization).
 *
 * Using @Serializable objects/classes instead of string routes means the
 * compiler checks our navigation arguments — no more stringly-typed "explorer/{id}"
 * templates. Detail routes (added in later milestones) will be data classes that
 * carry their arguments directly. See docs/ARCHITECTURE.md §5.
 */
@Serializable
data object Explorer

@Serializable
data object Packages

@Serializable
data object Workspace

@Serializable
data object Settings

/** Detail destination: read-only APK analysis. [refArg] is a StorageRefArgs token. */
@Serializable
data class ApkViewer(val refArg: String)

/**
 * The four top-level destinations shown in the bottom navigation bar. Each pairs
 * a type-safe route with its label and selected/unselected icons.
 */
enum class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    EXPLORER(
        route = Explorer,
        labelRes = R.string.nav_explorer,
        selectedIcon = FilledFolder,
        unselectedIcon = OutlinedFolder,
    ),
    PACKAGES(
        route = Packages,
        labelRes = R.string.nav_packages,
        selectedIcon = FilledApps,
        unselectedIcon = OutlinedApps,
    ),
    WORKSPACE(
        route = Workspace,
        labelRes = R.string.nav_workspace,
        selectedIcon = FilledBuild,
        unselectedIcon = OutlinedBuild,
    ),
    SETTINGS(
        route = Settings,
        labelRes = R.string.nav_settings,
        selectedIcon = FilledSettings,
        unselectedIcon = OutlinedSettings,
    ),
}
