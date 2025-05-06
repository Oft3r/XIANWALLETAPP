package net.xian.xianwalletapp.ui.theme

import androidx.compose.ui.graphics.Color

// Vibrant color palette for Xian Wallet
// Primary colors
val XianBlue = Color(0xFF1E88E5)       // Vibrant blue for text
val XianYellow = Color(0xFFFFC107)     // Bright yellow for buttons
val XianGreen = Color.White            // Was green, now changed to white
val XianOrange = Color(0xFFFF9800)     // Bright orange for highlights and warnings

// Background and surface colors
val XianDarkBackground = Color(0xFF1E1E1E)  // Dark background
val XianDarkSurface = Color(0xFF2D2D2D)    // Slightly lighter dark for surfaces

// Text and UI element colors
val XianPrimaryText = XianBlue          // Blue for primary text
val XianSecondaryText = Color.White     // Was green, now white
val XianTertiaryText = XianOrange       // Orange for tertiary text
val XianButtonColor = XianYellow        // Yellow for buttons
val XianErrorColor = Color(0xFFF44336)  // Red for errors

// Legacy colors (keeping for backward compatibility)
val PythonBlue = XianBlue
val PythonYellow = XianYellow
val PythonDarkBlue = Color(0xFF0D47A1)  // Darker blue
val PythonBlack = XianDarkBackground
val PythonDarkGrey = XianDarkSurface
val PythonKeyword = XianBlue
val PythonString = XianOrange
val PythonComment = XianGreen
val PythonFunction = XianYellow