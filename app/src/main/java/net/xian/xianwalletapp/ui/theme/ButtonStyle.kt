package net.xian.xianwalletapp.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Button types for Xian Wallet app
 */
enum class XianButtonType {
    PRIMARY,    // Yellow buttons
    SECONDARY,  // Blue buttons
    OUTLINED    // Outlined buttons
}

/**
 * Custom button colors for Xian Wallet app
 * Provides options for yellow (primary), blue (secondary), and outlined buttons
 */
@Composable
fun xianButtonColors(buttonType: XianButtonType = XianButtonType.PRIMARY): ButtonColors {
    return when (buttonType) {
        XianButtonType.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = XianButtonColor,
            contentColor = Color.Black
        )
        XianButtonType.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = XianBlue,
            contentColor = Color.White
        )
        XianButtonType.OUTLINED -> ButtonDefaults.outlinedButtonColors(
            contentColor = XianBlue
        )
    }
}