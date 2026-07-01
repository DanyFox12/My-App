package com.devexplorer.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devexplorer.app.settings.LocaleContext
import com.devexplorer.app.settings.SettingsStore
import com.devexplorer.app.settings.ThemeMode
import com.devexplorer.app.ui.DevExplorerApp
import com.devexplorer.core.designsystem.theme.DevExplorerTheme

/**
 * The single Activity for the whole app (single-Activity architecture).
 *
 * [attachBaseContext] applies the user's chosen language before any UI is
 * created; [onCreate] observes settings so the Material You / dynamic-color
 * preference takes effect immediately (theme recomposition, no restart needed).
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val languageTag = SettingsStore.readLanguage(newBase)
        super.attachBaseContext(LocaleContext.wrap(newBase, languageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system shows our branded splash
        // (Theme.DevExplorer.Starting) and then hands off to the app theme.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { SettingsStore.get(this) }
            val settings by store.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DevExplorerTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                DevExplorerApp()
            }
        }
    }
}
