package com.devexplorer.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.devexplorer.app.feature.apkviewer.ApkViewerScreen
import com.devexplorer.app.feature.explorer.ExplorerScreen
import com.devexplorer.app.feature.packages.PackagesScreen
import com.devexplorer.app.feature.settings.SettingsScreen
import com.devexplorer.app.feature.workspace.WorkspaceScreen
import com.devexplorer.core.model.StorageRef

/**
 * The app's single NavHost. Top-level tab destinations plus the ApkViewer detail
 * destination (pushed onto the back stack from the Explorer).
 *
 * Transitions are simple cross-fades — light enough to stay smooth on low-end
 * devices; richer per-feature motion comes later, gated by the device budget.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // Navigate to the APK viewer for a given source ref (a file or a package).
    val openApk: (StorageRef) -> Unit = { ref ->
        navController.navigate(ApkViewer(StorageRefArgs.encode(ref)))
    }

    NavHost(
        navController = navController,
        startDestination = Explorer,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<Explorer> { ExplorerScreen(onOpenApk = openApk) }
        composable<Packages> {
            PackagesScreen(
                onOpenPackage = { pkg -> openApk(StorageRef.installedPackage(pkg)) },
            )
        }
        composable<Workspace> { WorkspaceScreen() }
        composable<Settings> { SettingsScreen() }

        composable<ApkViewer> { backStackEntry ->
            val route = backStackEntry.toRoute<ApkViewer>()
            ApkViewerScreen(
                sourceRef = StorageRefArgs.decode(route.refArg),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
