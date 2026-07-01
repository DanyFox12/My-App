package com.devexplorer.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * App typography. We keep the default Material 3 scale (it's well-tuned for
 * legibility across densities) but expose a dedicated [monospace] style for the
 * code/manifest/hex views — an IDE-like surface needs a real monospaced face.
 */
val DevExplorerTypography = Typography()

/** Monospaced style for code, manifest dumps, certificate fingerprints, etc. */
val MonospaceTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)
