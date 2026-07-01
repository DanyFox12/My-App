package com.devexplorer.app.feature.codeviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devexplorer.core.designsystem.component.EmptyState
import com.devexplorer.core.designsystem.theme.MonospaceTextStyle
import com.devexplorer.core.designsystem.util.CodeLanguage
import com.devexplorer.core.designsystem.util.HighlightColors
import com.devexplorer.core.designsystem.util.highlightCode
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.TextDocument

@Composable
fun CodeViewerScreen(
    sourceRef: StorageRef,
    fileName: String,
    onBack: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: CodeViewerViewModel =
        viewModel(factory = CodeViewerViewModel.factory(appContext, sourceRef))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CodeViewerContent(state = state, fileName = fileName, onBack = onBack, onRetry = viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeViewerContent(
    state: CodeViewerUiState,
    fileName: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    title = "Couldn't open",
                    description = state.errorMessage,
                    action = { TextButton(onClick = onRetry) { Text("Try again") } },
                )
                state.document != null -> CodeContent(
                    document = state.document,
                    language = CodeLanguage.fromFileName(fileName),
                )
            }
        }
    }
}

@Composable
private fun CodeContent(document: TextDocument, language: CodeLanguage) {
    if (document.looksBinary) {
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "Binary file",
            description = "This doesn't look like text, so it isn't shown.",
        )
        return
    }

    // Per-line highlighting inside a LazyColumn: only visible lines are composed,
    // so even a large file scrolls smoothly. (Trade-off: multi-line block
    // comments/strings are highlighted per-line rather than across lines.)
    val lines = remember(document.content) { document.content.split('\n') }
    val colors = HighlightColors(
        plain = MaterialTheme.colorScheme.onSurface,
        keyword = MaterialTheme.colorScheme.primary,
        string = MaterialTheme.colorScheme.tertiary,
        comment = MaterialTheme.colorScheme.onSurfaceVariant,
        number = MaterialTheme.colorScheme.secondary,
    )
    val gutterWidth = when {
        lines.size >= 1000 -> 44.dp
        lines.size >= 100 -> 36.dp
        else -> 28.dp
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (document.truncated) {
            Text(
                text = "Large file — showing the first part only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines) { index, line ->
                val highlighted = remember(line, colors) { highlightCode(line, language, colors) }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text(
                        text = "${index + 1}",
                        style = MonospaceTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(gutterWidth)
                            .padding(end = 8.dp),
                    )
                    Text(
                        text = highlighted,
                        style = MonospaceTextStyle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
