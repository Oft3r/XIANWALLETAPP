package net.xian.xianwalletapp.ui.models

// Data class to keep minimized web page state (shared across app)
data class MinimizedPage(
    val title: String,
    val url: String,
    val state: android.os.Bundle
)

