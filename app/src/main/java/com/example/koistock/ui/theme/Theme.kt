package com.example.koistock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = SoftMint,
    tertiary = Lavender,
    background = DarkCanvas,
    surface = DarkSurface,
    surfaceVariant = Charcoal,
    outline = DarkBorder,
    onPrimary = CanvasWhite,
    onSecondary = Charcoal,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = Silver,
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    secondary = SoftMint,
    tertiary = Tangerine,
    background = CanvasWhite,
    surface = CanvasWhite,
    surfaceVariant = PaperMist,
    outline = Ash,
    outlineVariant = Smoke,
    onPrimary = CanvasWhite,
    onSecondary = Charcoal,
    onTertiary = CanvasWhite,
    onBackground = Charcoal,
    onSurface = Charcoal,
    onSurfaceVariant = Fog,
)

@Composable
fun KOIStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
