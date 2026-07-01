package com.devexplorer.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.devexplorer.app.feature.explorer.ExplorerScreen
import com.devexplorer.app.feature.packages.PackagesScreen
import com.devexplorer.app.feature.settings.SettingsScreen
import com.devexplorer.app.feature.workspace.WorkspaceScreen

/**
 * The app's single NavHost. For milestone 1 it wires the four top-level
 * screens; detail destinations (ApkViewer, CodeViewer, Signature, …) are added
 * in later milestones as their features land.
 *
 * Transitions are simple cross-fades for now — light enough to stay smooth on
 * low-end devices. Per-feature shared-element motion comes later and will be
 * gated by the device performance budget.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Explorer,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<Explorer> { ExplorerScreen() }
        composable<Packages> { PackagesScreen() }
        composable<Workspace> { WorkspaceScreen() }
        composable<Settings> { SettingsScreen() }
    }
}
