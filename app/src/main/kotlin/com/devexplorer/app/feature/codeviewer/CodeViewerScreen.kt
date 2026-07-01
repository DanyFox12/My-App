package com.devexplorer.app.feature.codeviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
    // Search is only meaningful once a text document is shown; the toggle is
    // hidden otherwise. State is hoisted here so the toolbar owns the toggle and
    // CodeContent owns the matching/scrolling.
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val canSearch = state.document != null && !state.document.looksBinary

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
                actions = {
                    if (canSearch) {
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                imageVector = if (searchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = if (searchActive) "Close search" else "Search in file",
                            )
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
                    title = "Couldn't open",
                    description = state.errorMessage,
                    action = { TextButton(onClick = onRetry) { Text("Try again") } },
                )
                state.document != null -> CodeContent(
                    document = state.document,
                    language = CodeLanguage.fromFileName(fileName),
                    searchActive = searchActive,
                    onCloseSearch = { searchActive = false },
                )
            }
        }
    }
}

@Composable
private fun CodeContent(
    document: TextDocument,
    language: CodeLanguage,
    searchActive: Boolean,
    onCloseSearch: () -> Unit,
) {
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

    // In-file search: line indices that contain the (case-insensitive) query.
    var query by rememberSaveable(searchActive) { mutableStateOf("") }
    val matches = remember(lines, query) {
        if (query.isBlank()) emptyList()
        else lines.mapIndexedNotNull { index, line ->
            index.takeIf { line.contains(query, ignoreCase = true) }
        }
    }
    // Reset the cursor whenever the match set changes (new query / new file).
    var matchCursor by remember(matches) { mutableIntStateOf(0) }
    val currentLine = matches.getOrNull(matchCursor)
    val matchLineSet = remember(matches) { matches.toHashSet() }

    val listState = rememberLazyListState()
    LaunchedEffect(currentLine) {
        currentLine?.let { listState.animateScrollToItem(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (searchActive) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                matchCount = matches.size,
                currentMatch = if (matches.isEmpty()) 0 else matchCursor + 1,
                onPrev = { if (matches.isNotEmpty()) matchCursor = (matchCursor - 1 + matches.size) % matches.size },
                onNext = { if (matches.isNotEmpty()) matchCursor = (matchCursor + 1) % matches.size },
                onClose = onCloseSearch,
            )
        }
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
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines) { index, line ->
                val highlighted = remember(line, colors) { highlightCode(line, language, colors) }
                val lineBackground = when {
                    index == currentLine -> MaterialTheme.colorScheme.primaryContainer
                    index in matchLineSet -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lineBackground)
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                ) {
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

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatch: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Auto-focus the field the first time the bar appears so the keyboard opens.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Find in file") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        Text(
            text = "$currentMatch/$matchCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onPrev, enabled = matchCount > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Close search")
        }
    }
}
