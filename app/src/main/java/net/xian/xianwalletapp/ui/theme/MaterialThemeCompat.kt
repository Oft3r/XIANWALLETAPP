package net.xian.xianwalletapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Provides a Material 3 theme for Composable views while maintaining backward compatibility 
 * with the app's AppCompat theme and resources.
 */
@Composable
fun XianMaterialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Use our predefined colors instead of resource-based ones
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = XianPrimary,
            secondary = XianSecondary,
            tertiary = XianPrimaryVariant,
            background = XianDarkBackground,
            surface = XianDarkSurface,
            onPrimary = XianPrimaryText,
            onSecondary = XianPrimaryText,
            onTertiary = XianPrimaryText,
            onBackground = XianPrimaryText,
            onSurface = XianPrimaryText,
            error = XianErrorColor,
            onError = XianPrimaryText
        )
    } else {
        lightColorScheme(
            primary = XianPrimary,
            secondary = XianSecondary,
            tertiary = XianPrimaryVariant,
            background = XianDarkBackground,
            surface = XianDarkSurface,
            onPrimary = XianPrimaryText,
            onSecondary = XianPrimaryText,
            onTertiary = XianPrimaryText,
            onBackground = XianPrimaryText,
            onSurface = XianPrimaryText,
            error = XianErrorColor,
            onError = XianPrimaryText
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
