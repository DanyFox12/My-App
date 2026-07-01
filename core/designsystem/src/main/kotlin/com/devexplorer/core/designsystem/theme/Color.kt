package com.devexplorer.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated brand palette — the *fallback* used whenever Material You dynamic
 * color isn't available (pre-Android 12, or when the user turns it off).
 *
 * The brand identity is a calm developer-tool teal/indigo, with a deliberately
 * contrasting warm amber reserved for the WORKSPACE zone so "you are now in the
 * writable sandbox" reads instantly, in any theme. See [WorkspaceAccent].
 */

// --- Brand seed tones (light) ---
internal val md_primary = Color(0xFF00696E)
internal val md_onPrimary = Color(0xFFFFFFFF)
internal val md_primaryContainer = Color(0xFF6FF6FE)
internal val md_onPrimaryContainer = Color(0xFF002022)

internal val md_secondary = Color(0xFF4A6365)
internal val md_onSecondary = Color(0xFFFFFFFF)
internal val md_secondaryContainer = Color(0xFFCCE8EA)
internal val md_onSecondaryContainer = Color(0xFF051F21)

internal val md_tertiary = Color(0xFF4B607C)
internal val md_onTertiary = Color(0xFFFFFFFF)
internal val md_tertiaryContainer = Color(0xFFD3E4FF)
internal val md_onTertiaryContainer = Color(0xFF041C35)

internal val md_error = Color(0xFFBA1A1A)
internal val md_onError = Color(0xFFFFFFFF)
internal val md_errorContainer = Color(0xFFFFDAD6)
internal val md_onErrorContainer = Color(0xFF410002)

internal val md_background = Color(0xFFF5FAFB)
internal val md_onBackground = Color(0xFF171D1D)
internal val md_surface = Color(0xFFF5FAFB)
internal val md_onSurface = Color(0xFF171D1D)
internal val md_surfaceVariant = Color(0xFFDAE4E5)
internal val md_onSurfaceVariant = Color(0xFF3F4849)
internal val md_outline = Color(0xFF6F797A)

// --- Brand seed tones (dark) ---
internal val md_primary_dark = Color(0xFF4CDAE1)
internal val md_onPrimary_dark = Color(0xFF00373A)
internal val md_primaryContainer_dark = Color(0xFF004F53)
internal val md_onPrimaryContainer_dark = Color(0xFF6FF6FE)

internal val md_secondary_dark = Color(0xFFB0CCCE)
internal val md_onSecondary_dark = Color(0xFF1B3436)
internal val md_secondaryContainer_dark = Color(0xFF324B4D)
internal val md_onSecondaryContainer_dark = Color(0xFFCCE8EA)

internal val md_tertiary_dark = Color(0xFFB3C8E9)
internal val md_onTertiary_dark = Color(0xFF1C314B)
internal val md_tertiaryContainer_dark = Color(0xFF334863)
internal val md_onTertiaryContainer_dark = Color(0xFFD3E4FF)

internal val md_error_dark = Color(0xFFFFB4AB)
internal val md_onError_dark = Color(0xFF690005)
internal val md_errorContainer_dark = Color(0xFF93000A)
internal val md_onErrorContainer_dark = Color(0xFFFFDAD6)

internal val md_background_dark = Color(0xFF0E1415)
internal val md_onBackground_dark = Color(0xFFDDE4E4)
internal val md_surface_dark = Color(0xFF0E1415)
internal val md_onSurface_dark = Color(0xFFDDE4E4)
internal val md_surfaceVariant_dark = Color(0xFF3F4849)
internal val md_onSurfaceVariant_dark = Color(0xFFBEC8C9)
internal val md_outline_dark = Color(0xFF899393)

/**
 * Zone accents live OUTSIDE the M3 color scheme on purpose: they must stay
 * recognizable even when dynamic color repaints everything else. System is
 * tinted with the primary; Workspace always gets this warm amber.
 */
object ZoneColors {
    val workspaceLight = Color(0xFF8A5100)
    val workspaceContainerLight = Color(0xFFFFDDB7)
    val onWorkspaceContainerLight = Color(0xFF2C1700)

    val workspaceDark = Color(0xFFFFB95C)
    val workspaceContainerDark = Color(0xFF683D00)
    val onWorkspaceContainerDark = Color(0xFFFFDDB7)
}
