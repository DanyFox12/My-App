package com.devexplorer.app.feature.packages

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A small in-memory cache of rasterized app icons, keyed by package name.
 *
 * Loading an app's icon touches its resources and rasterizes a (possibly
 * adaptive) drawable — too costly to redo every time a row scrolls back into
 * view. We rasterize once at a bounded size and keep a modest LRU so memory
 * stays predictable on low-heap devices.
 */
private object AppIconCache {
    private const val MAX_ENTRIES = 128
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)
    fun put(packageName: String, bitmap: ImageBitmap) = cache.put(packageName, bitmap)
}

private const val ICON_PX = 96

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager
        .getApplicationIcon(packageName)
        .toBitmap(ICON_PX, ICON_PX)
        .asImageBitmap()
}.getOrNull()

/**
 * Displays an installed app's icon, loaded off the main thread and cached. Only
 * composed rows load — so scrolling a long list stays cheap. Shows a neutral
 * placeholder until (or if) the icon is available.
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
            if (loaded != null) {
                AppIconCache.put(packageName, loaded)
                bitmap = loaded
            }
        }
    }

    val current = bitmap
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
