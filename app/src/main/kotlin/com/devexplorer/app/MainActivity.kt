package com.devexplorer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.devexplorer.app.ui.DevExplorerApp
import com.devexplorer.core.designsystem.theme.DevExplorerTheme

/**
 * The single Activity for the whole app (single-Activity architecture).
 *
 * Everything visible is Compose; navigation between "screens" happens inside the
 * Compose NavHost, not via multiple Activities. That gives us exactly one
 * Android lifecycle to reason about — ideal for studying it deeply.
 *
 * [enableEdgeToEdge] draws behind the system bars for a modern look; our theme
 * already makes the bars transparent, and the Scaffold consumes window insets so
 * content never sits under the status/navigation bars.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DevExplorerTheme {
                DevExplorerApp()
            }
        }
    }
}
