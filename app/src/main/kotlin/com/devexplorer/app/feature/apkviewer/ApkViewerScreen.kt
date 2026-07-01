package com.devexplorer.app.feature.apkviewer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.component.Treemap
import com.devexplorer.core.designsystem.component.TreemapSlice
import com.devexplorer.core.designsystem.util.formatBytes
import com.devexplorer.core.model.ApkPart
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.apkComposition
import com.devexplorer.core.model.buildApkReport
import com.devexplorer.core.model.ArchiveEntry
import com.devexplorer.core.model.CertificateInfo
import com.devexplorer.core.model.CompressionMethod
import com.devexplorer.core.model.DexStats
import com.devexplorer.core.model.SigningInfo
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.XmlNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/**
 * Fires a plain-text ACTION_SEND chooser with the analysis rendered by
 * [buildApkReport]. Read-only: the source archive is never opened for writing.
 */
private fun shareApkReport(context: Context, summary: ApkSummary) {
    val subject = (summary.appLabel ?: summary.packageName ?: "APK") + " — DevExplorer report"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, buildApkReport(summary))
    }
    context.startActivity(Intent.createChooser(send, "Share APK report"))
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
    val context = LocalContext.current
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
                actions = {
                    // The report is a read-only text snapshot; sharing never
                    // touches the source archive.
                    state.summary?.let { summary ->
                        IconButton(onClick = { shareApkReport(context, summary) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share report")
                        }
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
    val titles = listOf("Overview", "X-ray", "Manifest", "Permissions", "Signature", "Resources", "Contents")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
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
            1 -> XrayTab(summary.entries)
            2 -> ManifestTab(summary.manifest)
            3 -> PermissionsTab(summary.permissions)
            4 -> SignatureTab(summary.signingInfo)
            5 -> ResourcesTab(summary.entries)
            else -> ContentsTab(summary.entries)
        }
    }
}

private data class ManifestLine(val depth: Int, val text: String, val isElement: Boolean)

private fun flattenManifest(node: XmlNode, depth: Int, out: MutableList<ManifestLine>) {
    out += ManifestLine(depth, "<${node.name}>", isElement = true)
    node.attributes.forEach { out += ManifestLine(depth + 1, "${it.name} = ${it.value}", isElement = false) }
    node.children.forEach { flattenManifest(it, depth + 1, out) }
}

@Composable
private fun ManifestTab(manifest: XmlNode?) {
    if (manifest == null) {
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "No manifest",
            description = "Couldn't decode AndroidManifest.xml from this archive.",
        )
        return
    }
    val lines = remember(manifest) {
        buildList { flattenManifest(manifest, 0, this) }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(count = lines.size) { index ->
            val line = lines[index]
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (line.isElement) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (12 + line.depth * 14).dp,
                        end = 12.dp,
                        top = 2.dp,
                        bottom = 2.dp,
                    ),
            )
        }
    }
}

@Composable
private fun XrayTab(entries: List<ArchiveEntry>) {
    val slices = remember(entries) { apkComposition(entries) }
    if (slices.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = "Nothing to show",
            description = "This archive has no sized entries to visualize.",
        )
        return
    }
    val total = slices.sumOf { it.bytes }.coerceAtLeast(1)
    val treemapSlices = slices.map {
        TreemapSlice(it.part.label, it.bytes, partColor(it.part), Color.White)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${formatBytes(total)} uncompressed — sized by what's inside",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Treemap(
            slices = treemapSlices,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        slices.forEach { slice ->
            LegendRow(
                color = partColor(slice.part),
                label = slice.part.label,
                value = formatBytes(slice.bytes),
                percent = (slice.bytes * 100 / total).toInt(),
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String, percent: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Text(
            text = "$value · $percent%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun partColor(part: ApkPart): Color = when (part) {
    ApkPart.Dex -> Color(0xFF7E57C2)
    ApkPart.ResourceTable -> Color(0xFF26A69A)
    ApkPart.Resources -> Color(0xFF42A5F5)
    ApkPart.Native -> Color(0xFFFF7043)
    ApkPart.Assets -> Color(0xFF66BB6A)
    ApkPart.Signatures -> Color(0xFFEC407A)
    ApkPart.Other -> Color(0xFF78909C)
}

private data class ResourceOverview(
    val hasArsc: Boolean,
    val resByType: List<Pair<String, Int>>,
    val nativeLibsByAbi: List<Pair<String, Int>>,
    val assetCount: Int,
)

private fun resourceOverviewOf(entries: List<ArchiveEntry>): ResourceOverview {
    val files = entries.filter { !it.isDirectory }
    val resByType = files
        .filter { it.name.startsWith("res/") }
        .groupingBy { entry ->
            // res/drawable-hdpi/ic.png -> "drawable" (strip the config qualifier)
            entry.name.removePrefix("res/").substringBefore('/').substringBefore('-')
        }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
    val nativeLibsByAbi = files
        .filter { it.name.startsWith("lib/") }
        .groupingBy { it.name.removePrefix("lib/").substringBefore('/') }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
    return ResourceOverview(
        hasArsc = files.any { it.name == "resources.arsc" },
        resByType = resByType,
        nativeLibsByAbi = nativeLibsByAbi,
        assetCount = files.count { it.name.startsWith("assets/") },
    )
}

@Composable
private fun ResourcesTab(entries: List<ArchiveEntry>) {
    val overview = remember(entries) { resourceOverviewOf(entries) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                InfoRow("resources.arsc", if (overview.hasArsc) "present (compiled table)" else "absent")
                InfoRow("assets/ files", overview.assetCount.toString())
            }
        }

        if (overview.resByType.isNotEmpty()) {
            SectionTitle("Resources by type (res/)")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    overview.resByType.forEach { (type, count) ->
                        InfoRow(type, count.toString())
                    }
                }
            }
        }

        if (overview.nativeLibsByAbi.isNotEmpty()) {
            SectionTitle("Native libraries (lib/<abi>)")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    overview.nativeLibsByAbi.forEach { (abi, count) ->
                        InfoRow(abi, count.toString())
                    }
                }
            }
        }

        Text(
            text = "res/ holds compiled resources grouped by type and configuration " +
                "qualifier; resources.arsc is the compiled lookup table that maps resource " +
                "IDs to these entries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SignatureTab(signing: SigningInfo?) {
    if (signing == null) {
        EmptyState(
            icon = Icons.Outlined.VerifiedUser,
            title = "No signature",
            description = "No signing certificate was found for this archive.",
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SchemeChip("v1 (JAR)", signing.schemeV1)
            if (signing.hasMultipleSigners) SchemeChip("multiple signers", true)
        }
        signing.certificates.forEachIndexed { index, cert ->
            CertificateCard(index = index, total = signing.certificates.size, cert = cert)
        }
        Text(
            text = "Fingerprints are SHA-256/SHA-1 of the certificate — the same values apksigner reports.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CertificateCard(index: Int, total: Int, cert: CertificateInfo) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (total > 1) {
                Text(
                    text = "Certificate ${index + 1} of $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            InfoRow("Subject", cert.subject)
            InfoRow("Issuer", if (cert.isSelfSigned) "self-signed" else cert.issuer)
            InfoRow("Valid from", formatDate(cert.notBefore))
            InfoRow("Valid until", formatDate(cert.notAfter))
            InfoRow("Serial", cert.serialNumber)
            InfoRow("Sig. algorithm", cert.signatureAlgorithm)
            InfoRow("Key algorithm", cert.publicKeyAlgorithm)
            Fingerprint("SHA-256", cert.sha256)
            Fingerprint("SHA-1", cert.sha1)
        }
    }
}

@Composable
private fun Fingerprint(label: String, value: String) {
    val copy = rememberCopier()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { copy(label, value) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "$label · tap to copy",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Returns a (label, value) -> Unit that copies to the clipboard and toasts. */
@Composable
private fun rememberCopier(): (String, String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { label, value ->
            clipboard.setText(AnnotatedString(value))
            Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SchemeChip(label: String, enabled: Boolean) {
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

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
        summary.dexStats?.let { dex ->
            SectionTitle("DEX (methods count toward the 65,536-per-file limit)")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    InfoRow("Methods", dex.totalMethods.toString())
                    InfoRow("Classes", dex.totalClasses.toString())
                    InfoRow("Fields", dex.totalFields.toString())
                    dex.files.forEach { file ->
                        InfoRow(file.name, "${file.methodIds} methods · ${file.classDefs} classes")
                    }
                    if (dex.nearsMethodLimit) {
                        Text(
                            text = "A DEX file is near the ${DexStats.METHOD_REF_LIMIT} method-reference " +
                                "limit — adding code may force another DEX.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
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
    val copy = rememberCopier()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = permissions, key = { it }) { permission ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { copy("permission", permission) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
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
    var query by remember { mutableStateOf("") }
    val files = remember(entries) { entries.filter { !it.isDirectory }.sortedBy { it.name } }
    val visible = if (query.isBlank()) files else files.filter { it.name.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Filter entries") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = visible, key = { it.name }) { entry ->
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
