package net.xian.xianwalletapp.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = XianPrimary,
    secondary = XianSecondary,
    tertiary = XianPrimaryVariant,
    background = XianDarkBackground,
    surface = XianDarkSurface,
    onPrimary = XianDarkBackground,
    onSecondary = XianPrimaryText,
    onTertiary = XianDarkBackground,
    onBackground = XianPrimaryText,
    onSurface = XianPrimaryText,
    error = XianErrorColor,
    onError = XianPrimaryText
)

private val LightColorScheme = lightColorScheme(
    primary = XianPrimary,
    secondary = XianSecondary,
    tertiary = XianPrimaryVariant,
    background = XianDarkBackground,
    surface = XianDarkSurface,
    onPrimary = XianDarkBackground,
    onSecondary = XianPrimaryText,
    onTertiary = XianDarkBackground,
    onBackground = XianPrimaryText,
    onSurface = XianPrimaryText,
    error = XianErrorColor,
    onError = XianPrimaryText
)

@Composable
fun XIANWALLETAPPTheme(
    darkTheme: Boolean = true, // Always use dark theme by default for Python-inspired look
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic colors to ensure our Python theme is used
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }    // Apply system UI configuration
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar color is now set in the XML theme
            
            // Enable edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Optional: make status bar icons light/dark based on theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    // Use our Material theme bridge for compatibility
    XianMaterialTheme(
        darkTheme = darkTheme,
        content = content
    )
}