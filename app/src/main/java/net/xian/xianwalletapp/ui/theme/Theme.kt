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
    primary = XianBlue,
    secondary = XianGreen,
    tertiary = XianOrange,
    background = XianDarkBackground,
    surface = XianDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = XianPrimaryText,
    onSurface = XianSecondaryText,
    error = XianErrorColor,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = XianBlue,
    secondary = XianGreen,
    tertiary = XianOrange,
    background = XianDarkBackground,
    surface = XianDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = XianPrimaryText,
    onSurface = XianSecondaryText,
    error = XianErrorColor,
    onError = Color.White
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
            // Set status bar color
            window.statusBarColor = colorScheme.primary.toArgb()
            
            // Enable edge-to-edge display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Optional: make status bar icons light/dark based on theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}