package com.devexplorer.app.feature.packages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.devexplorer.app.R
import com.devexplorer.app.ui.ZoneScreenScaffold
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.model.Zone

/**
 * Packages — lists installed packages via PackageManager (added in milestone 5).
 * It's a System (read-only) view: we only inspect, never modify, installed apps.
 */
@Composable
fun PackagesScreen() {
    ZoneScreenScaffold(
        title = stringResource(R.string.packages_title),
        zone = Zone.System,
    ) { modifier ->
        EmptyState(
            icon = Icons.Outlined.Apps,
            title = stringResource(R.string.packages_empty_title),
            description = stringResource(R.string.packages_empty_desc),
            modifier = modifier,
        )
    }
}
