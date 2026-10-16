package com.example.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Forcing the custom Brutalist dark theme
private val LedgerColorScheme = darkColorScheme(
    background = OledBlack,
    surface = OledBlack,
    onBackground = StarkWhite,
    onSurface = StarkWhite,
    primary = AccentYellow,
    onPrimary = OledBlack, // Ensures text drawn on top of the accent color is black
    surfaceVariant = OledBlack, // Prevents Android from making sheets dark grey
    onSurfaceVariant = SlateGrey // Used for subtle UI text and metadata
)

@Composable
fun LedgerTheme(
    // We remove the dynamicColor and darkTheme boolean parameters
    // because this app strictly ignores system themes.
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LedgerColorScheme,
        typography = Typography, // This links to your Type.kt file
        content = content
    )
}