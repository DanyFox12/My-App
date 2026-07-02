package com.devexplorer.app.feature.apkviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.util.formatBytes
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.ArscStrings
import com.devexplorer.core.model.DexPackageNode
import com.devexplorer.core.model.NativeLibs
import com.devexplorer.core.model.SecurityCheck
import com.devexplorer.core.model.SecurityFinding
import com.devexplorer.core.model.SecuritySeverity
import com.devexplorer.core.model.securityAudit

// The four analysis tabs added on top of the original viewer: Security, DEX
// packages, Native libraries, and Resource strings. Same conventions as
// ApkViewerScreen.kt (cards, InfoRow, EmptyState, monospace for raw names).

// --- Security ---

@Composable
internal fun SecurityTab(summary: ApkSummary) {
    val findings = remember(summary) { securityAudit(summary) }
    if (findings.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.CheckCircle,
            title = "No findings",
            description = "Nothing risky stood out in the manifest or requested permissions.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = findings, key = { it.check.name }) { finding ->
            SecurityFindingCard(finding)
        }
        item {
            Text(
                text = "A read-only audit of the decoded manifest and permission list — " +
                    "no code from this APK is ever executed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecurityFindingCard(finding: SecurityFinding) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeverityChip(finding.severity)
                Text(
                    text = finding.check.title(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = finding.check.explanation(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            finding.items.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeverityChip(severity: SecuritySeverity) {
    val (color, contentColor, label) = when (severity) {
        SecuritySeverity.Warning -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "warning",
        )
        SecuritySeverity.Note -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "note",
        )
        SecuritySeverity.Info -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "info",
        )
    }
    Surface(color = color, contentColor = contentColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun SecurityCheck.title(): String = when (this) {
    SecurityCheck.Debuggable -> "Debuggable build"
    SecurityCheck.TestOnly -> "Test-only build"
    SecurityCheck.AllowBackup -> "Backup allowed"
    SecurityCheck.CleartextTraffic -> "Cleartext traffic allowed"
    SecurityCheck.ExportedComponents -> "Exported components without a permission"
    SecurityCheck.ExportedProviders -> "Exported content providers"
    SecurityCheck.DangerousPermissions -> "Sensitive permissions requested"
    SecurityCheck.OutdatedTargetSdk -> "Outdated target SDK"
    SecurityCheck.NoManifest -> "No manifest to audit"
}

private fun SecurityCheck.explanation(): String = when (this) {
    SecurityCheck.Debuggable -> "android:debuggable is on — anyone with adb can run-as this app and read its private data."
    SecurityCheck.TestOnly -> "android:testOnly is on — this build was made for instrumentation, not distribution."
    SecurityCheck.AllowBackup -> "App data can be pulled out via device backup (android:allowBackup)."
    SecurityCheck.CleartextTraffic -> "Plain-HTTP connections are permitted app-wide (android:usesCleartextTraffic)."
    SecurityCheck.ExportedComponents -> "Any installed app can invoke these components — no permission guards them:"
    SecurityCheck.ExportedProviders -> "Any installed app can query these content providers — data may be exposed:"
    SecurityCheck.DangerousPermissions -> "Permissions from the platform's dangerous bucket that this package requests:"
    SecurityCheck.OutdatedTargetSdk -> "Targeting an old SDK opts out of modern platform protections (runtime permissions, scoped storage, exported-by-default rules)."
    SecurityCheck.NoManifest -> "Couldn't decode AndroidManifest.xml from this archive, so the audit didn't run."
}

// --- DEX packages ---

@Composable
internal fun DexPackagesTab(
    root: DexPackageNode?,
    isLoading: Boolean,
    onLoad: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (root == null) {
        EmptyState(
            icon = Icons.Outlined.DataArray,
            title = "No DEX to map",
            description = "This archive has no parseable classes*.dex files.",
        )
        return
    }

    var expanded by rememberSaveable { mutableStateOf(setOf("")) }
    val rows = remember(root, expanded) {
        buildList { flattenDexTree(root.children, parentPath = "", depth = 0, expanded = expanded, out = this) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${root.totalClasses} classes · ${root.totalMethodRefs} method references — " +
                "tap a package to drill down",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = rows, key = { it.path }) { row ->
                DexPackageRow(
                    row = row,
                    onToggle = {
                        expanded = if (row.path in expanded) expanded - row.path else expanded + row.path
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

private data class DexRow(
    val path: String,
    val node: DexPackageNode,
    val depth: Int,
    val isExpanded: Boolean,
)

private fun flattenDexTree(
    nodes: List<DexPackageNode>,
    parentPath: String,
    depth: Int,
    expanded: Set<String>,
    out: MutableList<DexRow>,
) {
    for (node in nodes) {
        val path = if (parentPath.isEmpty()) node.name else "$parentPath.${node.name}"
        val isExpanded = path in expanded
        out += DexRow(path, node, depth, isExpanded)
        if (isExpanded) flattenDexTree(node.children, path, depth + 1, expanded, out)
    }
}

@Composable
private fun DexPackageRow(row: DexRow, onToggle: () -> Unit) {
    val node = row.node
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = node.children.isNotEmpty(), onClick = onToggle)
            .padding(start = (16 + row.depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (node.classCount > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${node.totalClasses} classes · ${node.totalMethodRefs} methods",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (node.children.isNotEmpty()) {
            Icon(
                imageVector = if (row.isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (row.isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Native libraries ---

@Composable
internal fun NativeLibsTab(libs: NativeLibs?) {
    if (libs == null || libs.files.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Memory,
            title = "No native code",
            description = "This archive ships no lib/ shared objects — it's pure bytecode.",
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${libs.files.size} libraries · ${formatBytes(libs.totalBytes)} uncompressed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        libs.summaries.forEach { abi ->
            SectionTitle("${abi.abi} — ${abi.fileCount} files · ${formatBytes(abi.totalBytes)}")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    PageSizeRow(abi.supports16KbPages)
                    libs.filesFor(abi.abi).forEach { lib ->
                        InfoRow(lib.fileName, nativeLibDetail(lib.sizeBytes, lib.elf?.machine, lib.elf?.is64Bit))
                    }
                }
            }
        }
        Text(
            text = "Bitness and machine come from each library's ELF header; 16 KB readiness " +
                "is the smallest PT_LOAD segment alignment (Android 15-class devices with " +
                "16 KB pages refuse 4 KB-aligned libraries).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun nativeLibDetail(sizeBytes: Long, machine: String?, is64Bit: Boolean?): String {
    val arch = when {
        machine == null || is64Bit == null -> "not an ELF?"
        else -> "$machine · ${if (is64Bit) "64-bit" else "32-bit"}"
    }
    return "${formatBytes(sizeBytes)} · $arch"
}

@Composable
private fun PageSizeRow(supports16Kb: Boolean?) {
    val text = when (supports16Kb) {
        true -> "16 KB page size: ready"
        false -> "16 KB page size: NOT ready (4 KB alignment)"
        null -> "16 KB page size: unknown"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = when (supports16Kb) {
            false -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// --- Resource strings ---

@Composable
internal fun ArscStringsTab(arsc: ArscStrings?) {
    if (arsc == null || arsc.strings.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = "No resource strings",
            description = "resources.arsc is absent or its string pool couldn't be decoded.",
        )
        return
    }
    var query by remember { mutableStateOf("") }
    val visible = remember(arsc, query) {
        if (query.isBlank()) arsc.strings
        else arsc.strings.filter { it.contains(query, ignoreCase = true) }
    }
    val copy = rememberCopier()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Filter ${arsc.strings.size} strings") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (arsc.truncated) {
            Text(
                text = "Showing the first ${arsc.strings.size} of ${arsc.totalCount} strings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(count = visible.size) { index ->
                val value = visible[index]
                Text(
                    text = value.ifBlank { "(blank)" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { copy("string", value) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider()
            }
        }
    }
}
