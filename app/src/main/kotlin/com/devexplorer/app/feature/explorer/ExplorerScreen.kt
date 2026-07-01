package com.devexplorer.app.feature.explorer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.devexplorer.core.designsystem.component.FileRow
import com.devexplorer.core.designsystem.component.ZoneBanner
import com.devexplorer.core.model.FileNode

/**
 * Explorer — stateful entry point. Owns the ViewModel and forwards its state to
 * the stateless [ExplorerContent]. Keeping the two apart makes the content
 * previewable and unit-friendly (docs/ARCHITECTURE.md §3).
 */
@Composable
fun ExplorerScreen() {
    val appContext = LocalContext.current.applicationContext
    val viewModel: ExplorerViewModel = viewModel(factory = ExplorerViewModel.factory(appContext))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExplorerContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplorerContent(
    state: ExplorerUiState,
    onEvent: (ExplorerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // SAF folder picker. The OS shows the system document UI; the result is a
    // tree Uri we translate into a StorageRef. We request no runtime storage
    // permission — SAF grants per-Uri access instead.
    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) onEvent(ExplorerEvent.TreePicked(uri.toString()))
    }

    // Route the system back gesture/button to "up" while we're below the root.
    BackHandler(enabled = state.canNavigateUp) { onEvent(ExplorerEvent.NavigateUp) }

    // Show one-shot messages (copy results) as a snackbar, then consume them so
    // they don't re-appear on recomposition/rotation.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onEvent(ExplorerEvent.ConsumeMessage)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.current?.name ?: stringResource(R.string.explorer_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.canNavigateUp) {
                        IconButton(onClick = { onEvent(ExplorerEvent.NavigateUp) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { pickFolder.launch(null) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Pick a folder")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ZoneBanner(
                zone = state.zone,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            if (state.hasRoot && state.breadcrumb.isNotEmpty()) {
                BreadcrumbRow(
                    crumbs = state.breadcrumb,
                    onCrumbClick = { onEvent(ExplorerEvent.NavigateToCrumb(it)) },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !state.hasRoot -> NoRootState(onPick = { pickFolder.launch(null) })
                    state.isLoading && state.entries.isEmpty() -> LoadingState()
                    state.errorMessage != null -> ErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(ExplorerEvent.Retry) },
                    )
                    state.isEmptyFolder -> EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = "Empty folder",
                        description = "There's nothing to show in this folder.",
                    )
                    else -> FileList(entries = state.entries, onEvent = onEvent)
                }

                // A slim top progress bar for refreshes that keep existing content.
                if (state.isLoading && state.entries.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileList(
    entries: List<FileNode>,
    onEvent: (ExplorerEvent) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = entries, key = { it.ref.raw }) { node ->
            FileRow(
                node = node,
                onClick = {
                    onEvent(
                        if (node.isDirectory) ExplorerEvent.OpenFolder(node)
                        else ExplorerEvent.OpenFile(node),
                    )
                },
                // Files (not folders) get a "copy into Workspace" action — the
                // read System zone's bridge into the writable sandbox.
                trailingContent = if (!node.isDirectory) {
                    {
                        IconButton(onClick = { onEvent(ExplorerEvent.CopyToWorkspace(node)) }) {
                            Icon(
                                imageVector = Icons.Outlined.MoveToInbox,
                                contentDescription = "Copy to Workspace",
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun BreadcrumbRow(
    crumbs: List<Crumb>,
    onCrumbClick: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(crumbs, key = { _, c -> c.ref.raw }) { index, crumb ->
            if (index > 0) {
                Text(
                    text = "  /  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val isLast = index == crumbs.lastIndex
            Text(
                text = crumb.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLast) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(enabled = !isLast) { onCrumbClick(index) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun NoRootState(onPick: () -> Unit) {
    EmptyState(
        icon = Icons.Outlined.FolderOpen,
        title = stringResource(R.string.explorer_empty_title),
        description = stringResource(R.string.explorer_empty_desc),
        action = {
            Button(onClick = onPick) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                Text(
                    text = "Choose a folder",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = "Something went wrong",
        description = message,
        action = { Button(onClick = onRetry) { Text("Try again") } },
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
