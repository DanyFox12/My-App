package com.devexplorer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devexplorer.core.designsystem.component.ZoneBanner
import com.devexplorer.core.model.Zone

/**
 * Shared per-screen chrome: a top app bar plus an always-visible [ZoneBanner]
 * when the screen belongs to a safety zone. Centralizing this guarantees every
 * screen advertises its System/Workspace zone identically — the core of the
 * read-only/writable visual contract.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneScreenScaffold(
    title: String,
    zone: Zone?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (zone != null) {
                ZoneBanner(
                    zone = zone,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                content(Modifier.fillMaxSize())
            }
        }
    }
}
