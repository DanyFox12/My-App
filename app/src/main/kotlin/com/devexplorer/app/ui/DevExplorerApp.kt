package com.devexplorer.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devexplorer.app.navigation.AppNavHost
import com.devexplorer.app.navigation.TopLevelDestination

/**
 * Root composable: a Scaffold hosting the bottom navigation bar and the NavHost.
 *
 * The bottom bar implements Material's multiple-back-stack pattern: each tab
 * keeps its own back stack, and re-selecting a tab restores its state instead of
 * rebuilding it (saveState/restoreState). launchSingleTop avoids stacking
 * duplicate copies of a top-level destination.
 */
@Composable
fun DevExplorerApp(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val destinations = TopLevelDestination.entries

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                destinations.forEach { dest ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.hasRoute(dest.route::class) } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                // Pop up to the start so the back stack doesn't grow
                                // unbounded when switching tabs; save each tab's state.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
