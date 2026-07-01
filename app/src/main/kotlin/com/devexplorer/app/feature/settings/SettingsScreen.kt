package com.devexplorer.app.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.devexplorer.app.R
import com.devexplorer.app.ui.ZoneScreenScaffold
import com.devexplorer.core.designsystem.component.EmptyState

/**
 * Settings — theme, language, and the network-feature opt-in toggle (built out
 * in milestone 10). It has no safety zone, so no zone banner is shown.
 */
@Composable
fun SettingsScreen() {
    ZoneScreenScaffold(
        title = stringResource(R.string.settings_title),
        zone = null,
    ) { modifier ->
        EmptyState(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.settings_empty_title),
            description = stringResource(R.string.settings_empty_desc),
            modifier = modifier,
        )
    }
}
