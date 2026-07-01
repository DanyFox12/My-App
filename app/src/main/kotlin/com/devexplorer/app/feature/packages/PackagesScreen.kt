package com.devexplorer.app.feature.packages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devexplorer.app.R
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.component.ZoneBanner
import com.devexplorer.core.model.InstalledPackage
import com.devexplorer.core.model.Zone

@Composable
fun PackagesScreen(
    onOpenPackage: (String) -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: PackagesViewModel = viewModel(factory = PackagesViewModel.factory(appContext))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PackagesContent(state = state, onEvent = viewModel::onEvent, onOpenPackage = onOpenPackage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackagesContent(
    state: PackagesUiState,
    onEvent: (PackagesEvent) -> Unit,
    onOpenPackage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.packages_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ZoneBanner(
                zone = Zone.System,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(PackagesEvent.SetQuery(it)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Show system apps", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.includeSystem,
                    onCheckedChange = { onEvent(PackagesEvent.SetIncludeSystem(it)) },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.allPackages.isEmpty() -> LoadingState()
                    state.errorMessage != null -> EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Something went wrong",
                        description = state.errorMessage,
                        action = { TextButton(onClick = { onEvent(PackagesEvent.Refresh) }) { Text("Try again") } },
                    )
                    state.isEmpty -> EmptyState(
                        icon = Icons.Outlined.Apps,
                        title = stringResource(R.string.packages_empty_title),
                        description = if (state.query.isBlank()) {
                            stringResource(R.string.packages_empty_desc)
                        } else {
                            "No apps match \"${state.query}\"."
                        },
                    )
                    else -> PackagesList(packages = state.visible, onOpenPackage = onOpenPackage)
                }
            }
        }
    }
}

@Composable
private fun PackagesList(
    packages: List<InstalledPackage>,
    onOpenPackage: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = packages, key = { it.packageName }) { pkg ->
            PackageRow(pkg = pkg, onClick = { onOpenPackage(pkg.packageName) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun PackageRow(
    pkg: InstalledPackage,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pkg.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pkg.isSystem) SystemBadge()
        }
        Text(
            text = pkg.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "v${pkg.versionName ?: "?"} (${pkg.versionCode})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SystemBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "SYSTEM",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
