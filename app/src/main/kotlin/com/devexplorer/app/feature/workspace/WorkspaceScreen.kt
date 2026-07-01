package com.devexplorer.app.feature.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devexplorer.app.R
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.component.FileRow
import com.devexplorer.core.designsystem.component.ZoneBanner
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.WorkspaceItem
import com.devexplorer.core.model.Zone

@Composable
fun WorkspaceScreen() {
    val appContext = LocalContext.current.applicationContext
    val viewModel: WorkspaceViewModel = viewModel(factory = WorkspaceViewModel.factory(appContext))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-list whenever the tab becomes visible, so items copied in from the
    // Explorer show up without a manual refresh.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onEvent(WorkspaceEvent.Refresh)
    }

    WorkspaceContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceContent(
    state: WorkspaceUiState,
    onEvent: (WorkspaceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onEvent(WorkspaceEvent.ConsumeMessage)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.workspace_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ZoneBanner(
                zone = Zone.Workspace,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.items.isEmpty() -> LoadingState()
                    state.errorMessage != null -> EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Something went wrong",
                        description = state.errorMessage,
                        action = { TextButton(onClick = { onEvent(WorkspaceEvent.Refresh) }) { Text("Try again") } },
                    )
                    state.isEmpty -> EmptyState(
                        icon = Icons.Outlined.Inventory2,
                        title = stringResource(R.string.workspace_empty_title),
                        description = stringResource(R.string.workspace_empty_desc),
                    )
                    else -> WorkspaceList(items = state.items, onEvent = onEvent)
                }
            }
        }
    }

    // Dialogs are driven purely by state, so they survive recomposition.
    state.renameTarget?.let { target ->
        RenameDialog(
            item = target,
            onConfirm = { newName -> onEvent(WorkspaceEvent.ConfirmRename(target, newName)) },
            onDismiss = { onEvent(WorkspaceEvent.DismissDialog) },
        )
    }
    state.deleteTarget?.let { target ->
        DeleteDialog(
            item = target,
            onConfirm = { onEvent(WorkspaceEvent.ConfirmDelete(target)) },
            onDismiss = { onEvent(WorkspaceEvent.DismissDialog) },
        )
    }
}

@Composable
private fun WorkspaceList(
    items: List<WorkspaceItem>,
    onEvent: (WorkspaceEvent) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id }) { item ->
            FileRow(
                node = FileNode(
                    ref = item.ref,
                    name = item.name,
                    isDirectory = false,
                    sizeBytes = item.sizeBytes,
                    lastModified = item.addedAt,
                ),
                onClick = { /* opening a workspace item arrives in later milestones */ },
                trailingContent = { ItemMenu(item = item, onEvent = onEvent) },
            )
        }
    }
}

@Composable
private fun ItemMenu(
    item: WorkspaceItem,
    onEvent: (WorkspaceEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEvent(WorkspaceEvent.RequestRename(item))
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEvent(WorkspaceEvent.RequestDelete(item))
                },
            )
        }
    }
}

@Composable
private fun RenameDialog(
    item: WorkspaceItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seed the field from the current name; reset if the target changes.
    var text by remember(item.id) { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("New name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(
    item: WorkspaceItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete item") },
        text = { Text("Delete \"${item.name}\" from the Workspace? This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
