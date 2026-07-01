package com.devexplorer.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
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

/** Detail destination: read-only text/code viewer. [refArg] is a StorageRefArgs token. */
@Serializable
data class CodeViewer(val refArg: String, val name: String)

/** Detail destination: reverse permission lookup across installed apps. */
@Serializable
data object PermissionSearch

/** Detail destination: pick two installed apps and diff them (read-only). */
@Serializable
data object ApkCompare

/** Detail destination: edit a Workspace sandbox file in place. */
@Serializable
data class WorkspaceEditor(val id: String, val name: String)

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
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
    ),
    PACKAGES(
        route = Packages,
        labelRes = R.string.nav_packages,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
    ),
    WORKSPACE(
        route = Workspace,
        labelRes = R.string.nav_workspace,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
    ),
    SETTINGS(
        route = Settings,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}
