package com.devexplorer.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.devexplorer.core.designsystem.performance.LocalPerformanceBudget
import com.devexplorer.app.feature.apkcompare.ApkCompareScreen
import com.devexplorer.app.feature.apkviewer.ApkViewerScreen
import com.devexplorer.app.feature.codeviewer.CodeViewerScreen
import com.devexplorer.app.feature.explorer.ExplorerScreen
import com.devexplorer.app.feature.packages.PackagesScreen
import com.devexplorer.app.feature.permissions.PermissionSearchScreen
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
    // Navigate to the code viewer for a text file.
    val openText: (StorageRef, String) -> Unit = { ref, name ->
        navController.navigate(CodeViewer(StorageRefArgs.encode(ref), name))
    }

    // Scale transition duration to the device: 0ms (snap) on low-end phones.
    val fade = tween<Float>(durationMillis = LocalPerformanceBudget.current.crossfadeMillis)

    NavHost(
        navController = navController,
        startDestination = Explorer,
        modifier = modifier,
        enterTransition = { fadeIn(fade) },
        exitTransition = { fadeOut(fade) },
    ) {
        composable<Explorer> { ExplorerScreen(onOpenApk = openApk, onOpenText = openText) }
        composable<Packages> {
            PackagesScreen(
                onOpenPackage = { pkg -> openApk(StorageRef.installedPackage(pkg)) },
                onOpenPermissionUsage = { navController.navigate(PermissionSearch) },
                onOpenCompare = { navController.navigate(ApkCompare) },
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

        composable<CodeViewer> { backStackEntry ->
            val route = backStackEntry.toRoute<CodeViewer>()
            CodeViewerScreen(
                sourceRef = StorageRefArgs.decode(route.refArg),
                fileName = route.name,
                onBack = { navController.popBackStack() },
            )
        }

        composable<PermissionSearch> {
            PermissionSearchScreen(onBack = { navController.popBackStack() })
        }

        composable<ApkCompare> {
            ApkCompareScreen(onBack = { navController.popBackStack() })
        }
    }
}
