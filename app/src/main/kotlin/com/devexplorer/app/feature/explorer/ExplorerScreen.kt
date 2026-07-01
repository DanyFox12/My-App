package com.devexplorer.app.feature.explorer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.devexplorer.app.R
import com.devexplorer.app.ui.ZoneScreenScaffold
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.model.Zone

/**
 * Explorer — the System (read-only) file browser.
 *
 * Milestone 1 renders the chrome and an empty state. Milestone 2 wires the
 * Storage Access Framework (folder picking + listing) behind a ViewModel and
 * use-cases, keeping this composable a pure function of UI state.
 */
@Composable
fun ExplorerScreen() {
    ZoneScreenScaffold(
        title = stringResource(R.string.explorer_title),
        zone = Zone.System,
    ) { modifier ->
        EmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = stringResource(R.string.explorer_empty_title),
            description = stringResource(R.string.explorer_empty_desc),
            modifier = modifier,
        )
    }
}
