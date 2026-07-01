package com.devexplorer.app.feature.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.devexplorer.app.R
import com.devexplorer.app.ui.ZoneScreenScaffold
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.model.Zone

/**
 * Workspace — the ONLY writable zone (the app-private sandbox). Its banner uses
 * the warm amber accent so the switch from read-only System to writable
 * Workspace is unmistakable. Sandbox file management lands in milestone 3.
 */
@Composable
fun WorkspaceScreen() {
    ZoneScreenScaffold(
        title = stringResource(R.string.workspace_title),
        zone = Zone.Workspace,
    ) { modifier ->
        EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = stringResource(R.string.workspace_empty_title),
            description = stringResource(R.string.workspace_empty_desc),
            modifier = modifier,
        )
    }
}
