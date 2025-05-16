package net.xian.xianwalletapp.ui.theme

import androidx.compose.ui.graphics.Color

// Modern color palette for Xian Wallet (based on design)
// Primary colors
val XianPrimary = Color(0xFF56D5C9)       // Teal primary color
val XianPrimaryVariant = Color(0xFF8CDDD8) // Light teal primary variant
val XianSecondary = Color(0xFF1F1F1F)     // Dark gray for surfaces
val XianSecondaryVariant = Color(0xFF333333) // Slightly lighter dark gray

// Background and surface colors
val XianDarkBackground = Color(0xFF121212)  // Dark background
val XianDarkSurface = Color(0xFF1F1F1F)     // Dark surface color
val XianSurfaceElevated = Color(0xFF2D2D2D) // Slightly elevated surface color

// Text and UI element colors
val XianPrimaryText = Color(0xFFF5F5F5)     // Light text for primary content
val XianSecondaryText = Color(0xFF999999)   // Gray text for secondary content
val XianTertiaryText = XianPrimaryVariant   // Light teal for highlights
val XianButtonColor = XianPrimary           // Teal for buttons
val XianErrorColor = Color(0xFFF44336)      // Keeping red for errors

// Legacy colors (keeping for backward compatibility)
val PythonBlue = XianPrimary         // Changed to teal
val PythonYellow = XianPrimaryVariant // Changed to light teal
val PythonDarkBlue = Color(0xFF2C8C84) // Darker teal variant
val PythonBlack = XianDarkBackground
val PythonDarkGrey = XianDarkSurface
val PythonKeyword = XianPrimary
val PythonString = XianPrimaryVariant
val PythonComment = XianPrimaryText
val PythonFunction = XianPrimary