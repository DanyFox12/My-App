package com.devexplorer.app.feature.apkcompare

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.util.formatBytes
import com.devexplorer.core.model.ApkDiff
import com.devexplorer.core.model.FieldChange
import com.devexplorer.core.model.InstalledPackage
import com.devexplorer.core.model.SigningComparison
import com.devexplorer.core.model.buildApkDiffReport
import com.devexplorer.core.usecase.CompareApkUseCase

@Composable
fun ApkCompareScreen(onBack: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: ApkCompareViewModel =
        viewModel(factory = ApkCompareViewModel.factory(appContext))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ApkCompareContent(state = state, onBack = onBack, onEvent = viewModel::onEvent)
}

private fun shareDiffReport(context: Context, comparison: CompareApkUseCase.Comparison) {
    val oldName = comparison.old.appLabel ?: comparison.old.packageName ?: "old"
    val newName = comparison.new.appLabel ?: comparison.new.packageName ?: "new"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "APK comparison — DevExplorer")
        putExtra(Intent.EXTRA_TEXT, buildApkDiffReport(comparison.diff, oldName, newName))
    }
    context.startActivity(Intent.createChooser(send, "Share comparison"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkCompareContent(
    state: ApkCompareUiState,
    onBack: () -> Unit,
    onEvent: (ApkCompareEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Compare APKs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.comparison?.let { comparison ->
                        IconButton(onClick = { shareDiffReport(context, comparison) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share comparison")
                        }
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
            SelectionBar(state = state, onClear = { onEvent(ApkCompareEvent.ClearSelection) })
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading || state.isComparing -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.errorMessage != null -> EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Couldn't compare",
                        description = state.errorMessage,
                        action = {
                            TextButton(onClick = { onEvent(ApkCompareEvent.ClearSelection) }) {
                                Text("Start over")
                            }
                        },
                    )
                    state.comparison != null -> DiffResult(state.comparison.diff)
                    else -> PackagePicker(state = state, onPick = { onEvent(ApkCompareEvent.Pick(it)) })
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(state: ApkCompareUiState, onClear: () -> Unit) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slot(letter = "A", label = state.old?.label, modifier = Modifier.weight(1f))
            Slot(letter = "B", label = state.new?.label, modifier = Modifier.weight(1f))
            if (state.old != null || state.new != null) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun Slot(letter: String, label: String?, modifier: Modifier = Modifier) {
    Surface(
        color = if (label != null) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (label != null) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            text = if (label != null) "$letter · $label" else "$letter · pick an app",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PackagePicker(state: ApkCompareUiState, onPick: (InstalledPackage) -> Unit) {
    if (state.packages.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Difference,
            title = "No apps to compare",
            description = "No user-installed apps were found on this device.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = state.packages, key = { it.packageName }) { pkg ->
            val selectedAs = when {
                state.old?.ref?.raw == pkg.packageName -> "A"
                state.new?.ref?.raw == pkg.packageName -> "B"
                else -> null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(pkg) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pkg.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pkg.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selectedAs != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = selectedAs,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffResult(diff: ApkDiff) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (diff.isIdentical) {
            Text(
                text = "These two APKs have no meaningful differences.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionTitle("Identity")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                diff.identity.forEach { ChangeRow(it) }
            }
        }

        SectionTitle("Size")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val s = diff.sizes
                DeltaRow(
                    "Uncompressed",
                    formatBytes(s.oldUncompressed),
                    formatBytes(s.newUncompressed),
                    s.uncompressedDelta,
                )
                DeltaRow("Compressed", formatBytes(s.oldCompressed), formatBytes(s.newCompressed), s.compressedDelta)
                DeltaRow("Entries", s.oldEntries.toString(), s.newEntries.toString(), s.entriesDelta.toLong())
            }
        }

        SectionTitle("DEX")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                val d = diff.dex
                DeltaRow("Methods", d.oldMethods.toString(), d.newMethods.toString(), d.methodsDelta.toLong())
                DeltaRow("Classes", d.oldClasses.toString(), d.newClasses.toString(), d.classesDelta.toLong())
                DeltaRow("DEX files", d.oldDexFiles.toString(), d.newDexFiles.toString(), d.dexFilesDelta.toLong())
            }
        }

        SectionTitle("Permissions")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (diff.permissionsAdded.isEmpty() && diff.permissionsRemoved.isEmpty()) {
                    Text("No permission changes.", style = MaterialTheme.typography.bodyMedium)
                }
                diff.permissionsAdded.forEach { PermissionDelta("+ $it", MaterialTheme.colorScheme.primary) }
                diff.permissionsRemoved.forEach { PermissionDelta("− $it", MaterialTheme.colorScheme.error) }
                Text(
                    text = "${diff.permissionsCommon} unchanged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("Signing")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = when (diff.signing) {
                    SigningComparison.Same -> "Same certificate set — same author."
                    SigningComparison.Different -> "DIFFERENT certificate set — re-signed or a different author."
                    SigningComparison.Unknown -> "Not comparable (one or both APKs unsigned or unreadable)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (diff.signing == SigningComparison.Different) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun ChangeRow(change: FieldChange) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = change.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (change.changed) "${change.oldValue ?: "—"} → ${change.newValue ?: "—"}"
            else (change.oldValue ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (change.changed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DeltaRow(label: String, old: String, new: String, delta: Long) {
    val sign = if (delta > 0) "+" else ""
    val changed = delta != 0L
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
            text = if (changed) "$old → $new ($sign$delta)" else old,
            style = MaterialTheme.typography.bodyMedium,
            color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PermissionDelta(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}
