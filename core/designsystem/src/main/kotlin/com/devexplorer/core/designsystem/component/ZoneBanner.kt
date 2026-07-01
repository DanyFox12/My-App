package com.devexplorer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.devexplorer.core.designsystem.theme.DevExplorerTheme
import com.devexplorer.core.designsystem.theme.ZoneColors
import com.devexplorer.core.model.Zone

/**
 * A compact, always-visible badge declaring which safety zone the current
 * screen is in. This is the single most important piece of the design system:
 * it makes "read-only System" vs "writable Workspace" impossible to confuse.
 *
 * The Workspace variant uses the warm amber zone accent (outside the dynamic
 * color scheme) so it stays recognizable in every theme.
 */
@Composable
fun ZoneBanner(
    zone: Zone,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val (container, onContainer, icon, label, description) = when (zone) {
        Zone.System -> ZoneVisuals(
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Filled.Lock,
            label = "System · read-only",
            description = "System zone. Files here are read-only and cannot be modified.",
        )
        Zone.Workspace -> ZoneVisuals(
            container = if (dark) ZoneColors.workspaceContainerDark else ZoneColors.workspaceContainerLight,
            onContainer = if (dark) ZoneColors.onWorkspaceContainerDark else ZoneColors.onWorkspaceContainerLight,
            icon = Icons.Outlined.Edit,
            label = "Workspace · sandbox",
            description = "Workspace zone. This is your private sandbox; changes here are allowed.",
        )
    }

    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = onContainer)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = onContainer,
        )
    }
}

private data class ZoneVisuals(
    val container: Color,
    val onContainer: Color,
    val icon: ImageVector,
    val label: String,
    val description: String,
)

@Preview(name = "System zone")
@Composable
private fun PreviewSystemBanner() {
    DevExplorerTheme { ZoneBanner(Zone.System) }
}

@Preview(name = "Workspace zone")
@Composable
private fun PreviewWorkspaceBanner() {
    DevExplorerTheme { ZoneBanner(Zone.Workspace) }
}
