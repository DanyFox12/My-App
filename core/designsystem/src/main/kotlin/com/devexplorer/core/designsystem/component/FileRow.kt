package com.devexplorer.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.devexplorer.core.designsystem.theme.DevExplorerTheme
import com.devexplorer.core.model.FileCategory
import com.devexplorer.core.model.FileNode
import com.devexplorer.core.model.StorageRef

/**
 * One row in a directory/archive listing. Stateless: it renders a [FileNode]
 * and forwards a click. Icon + tint are derived from [FileNode.category] so the
 * visual language for "this is an APK / archive / folder / text file" is
 * consistent across every screen that lists files.
 */
@Composable
fun FileRow(
    node: FileNode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = node.category.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            node.subtitle()?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailingContent != null -> trailingContent()
            node.isDirectory -> Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.Directory -> Icons.Filled.Folder
    FileCategory.Apk -> Icons.Filled.Android
    FileCategory.Archive -> Icons.Filled.FolderZip
    FileCategory.Text -> Icons.Filled.Description
    FileCategory.Other -> Icons.Filled.InsertDriveFile
}

/** Human-friendly secondary line (size for files, nothing for plain folders). */
private fun FileNode.subtitle(): String? {
    val size = sizeBytes ?: return null
    return formatBytes(size)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return "%.1f %s".format(value, units[i])
}

@Preview
@Composable
private fun PreviewFileRows() {
    DevExplorerTheme {
        Column {
            FileRow(
                node = FileNode(StorageRef.workspace("lib"), "lib", isDirectory = true),
                onClick = {},
            )
            FileRow(
                node = FileNode(
                    StorageRef.workspace("app.apk"), "app-release.apk",
                    isDirectory = false, sizeBytes = 8_400_000,
                    mimeType = FileNode.APK_MIME,
                ),
                onClick = {},
            )
            FileRow(
                node = FileNode(
                    StorageRef.workspace("Main.kt"), "MainActivity.kt",
                    isDirectory = false, sizeBytes = 2_048,
                ),
                onClick = {},
            )
        }
    }
}
