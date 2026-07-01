package com.devexplorer.app.feature.apkviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.util.formatBytes
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.ArchiveEntry
import com.devexplorer.core.model.CompressionMethod
import com.devexplorer.core.model.StorageRef

@Composable
fun ApkViewerScreen(
    sourceRef: StorageRef,
    onBack: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: ApkViewerViewModel =
        viewModel(factory = ApkViewerViewModel.factory(appContext, sourceRef))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ApkViewerContent(state = state, onBack = onBack, onRetry = viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkViewerContent(
    state: ApkViewerUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = state.summary?.appLabel ?: state.summary?.packageName ?: "APK"
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Couldn't analyze",
                    description = state.errorMessage,
                    action = { TextButton(onClick = onRetry) { Text("Try again") } },
                )
                state.summary != null -> ApkTabs(state.summary)
            }
        }
    }
}

@Composable
private fun ApkTabs(summary: ApkSummary) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Overview", "Permissions", "Contents")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label) },
                )
            }
        }
        when (tab) {
            0 -> OverviewTab(summary)
            1 -> PermissionsTab(summary.permissions)
            else -> ContentsTab(summary.entries)
        }
    }
}

@Composable
private fun OverviewTab(summary: ApkSummary) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!summary.isValidPackage) {
            Text(
                text = "This archive isn't a recognized Android package, but its ZIP contents are shown under Contents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                InfoRow("Package", summary.packageName ?: "—")
                summary.appLabel?.let { InfoRow("Label", it) }
                InfoRow("Version name", summary.versionName ?: "—")
                InfoRow("Version code", summary.versionCode?.toString() ?: "—")
                InfoRow("Min SDK", summary.minSdk?.toString() ?: "—")
                InfoRow("Target SDK", summary.targetSdk?.toString() ?: "—")
                summary.compileSdk?.let { InfoRow("Compile SDK", it.toString()) }
            }
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                InfoRow("Entries", summary.entryCount.toString())
                InfoRow("DEX files", summary.dexCount.toString())
                InfoRow("Uncompressed", formatBytes(summary.totalUncompressedBytes))
                InfoRow("Compressed", formatBytes(summary.totalCompressedBytes))
                InfoRow("resources.arsc", if (summary.hasResourcesArsc) "present" else "absent")
                InfoRow("Signature files", summary.signatureFiles.size.toString())
            }
        }
        Text(
            text = "Read-only analysis — this file was not modified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PermissionsTab(permissions: List<String>) {
    if (permissions.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Key,
            title = "No permissions",
            description = "This package doesn't request any permissions.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = permissions, key = { it }) { permission ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = permission.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = permission,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ContentsTab(entries: List<ArchiveEntry>) {
    val files = entries.filter { !it.isDirectory }.sortedBy { it.name }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = files, key = { it.name }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (entry.isNotable()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatBytes(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MethodChip(entry.method)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun MethodChip(method: CompressionMethod) {
    val label = when (method) {
        CompressionMethod.Stored -> "STORED"
        CompressionMethod.Deflated -> "DEFLATED"
        CompressionMethod.Other -> "OTHER"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Entries worth visually highlighting in the contents list. */
private fun ArchiveEntry.isNotable(): Boolean =
    name == "AndroidManifest.xml" ||
        name == "resources.arsc" ||
        name.endsWith(".dex") ||
        name.startsWith("META-INF/")
