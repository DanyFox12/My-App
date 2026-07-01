package com.devexplorer.app.feature.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devexplorer.app.R
import com.devexplorer.app.settings.SettingsStore
import com.devexplorer.app.settings.ThemeMode
import com.devexplorer.app.ui.ZoneScreenScaffold

/**
 * Settings — appearance (dynamic color), language (per-app locale), and the
 * network-features opt-in. Reads/writes the process-wide [SettingsStore]. The
 * Settings screen has no safety zone, so no zone banner is shown.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()

    ZoneScreenScaffold(
        title = stringResource(R.string.settings_title),
        zone = null,
    ) { modifier ->
        Column(modifier = modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_theme))
            // Theme mode: Follow system / Light / Dark
            ThemeModeOption(ThemeMode.System, R.string.settings_theme_system, settings.themeMode) { store.setThemeMode(it) }
            ThemeModeOption(ThemeMode.Light, R.string.settings_theme_light, settings.themeMode) { store.setThemeMode(it) }
            ThemeModeOption(ThemeMode.Dark, R.string.settings_theme_dark, settings.themeMode) { store.setThemeMode(it) }

            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                description = if (dynamicSupported) {
                    stringResource(R.string.settings_dynamic_color_desc)
                } else {
                    stringResource(R.string.settings_dynamic_color_unavailable)
                },
                checked = settings.dynamicColor && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = { store.setDynamicColor(it) },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_language))
            LanguageOption("", R.string.settings_language_system, settings.language, context)
            LanguageOption("en", R.string.settings_language_english, settings.language, context)
            LanguageOption("ar", R.string.settings_language_arabic, settings.language, context)

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_network))
            SwitchRow(
                title = stringResource(R.string.settings_network),
                description = stringResource(R.string.settings_network_desc),
                checked = settings.networkEnabled,
                enabled = true,
                onCheckedChange = { store.setNetworkEnabled(it) },
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    labelRes: Int,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val selected = current == mode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { if (!selected) onSelect(mode) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun LanguageOption(
    tag: String,
    labelRes: Int,
    current: String,
    context: android.content.Context,
) {
    val store = remember { SettingsStore.get(context) }
    val selected = current == tag
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = {
                    if (!selected) {
                        store.setLanguage(tag)
                        // Recreate so attachBaseContext re-applies the new locale.
                        (context as? Activity)?.recreate()
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
