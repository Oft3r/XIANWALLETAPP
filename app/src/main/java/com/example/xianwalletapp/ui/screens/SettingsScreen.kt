package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
// Keep necessary imports
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.NetworkCheck
// import androidx.compose.material.icons.filled.ChevronRight // Optional, can be added later if needed
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Send

import androidx.compose.material3.*
import androidx.compose.runtime.* // Import remember, mutableStateOf, etc.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xianwalletapp.R // Import R for drawable resources
import androidx.navigation.NavController
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.CoroutineScope // Add CoroutineScope import
import kotlinx.coroutines.launch

/**
 * Settings screen for the Xian Wallet app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    walletManager: WalletManager, // Keep walletManager
    networkService: XianNetworkService, // Keep networkService
    snackbarHostState: SnackbarHostState, // Add snackbarHostState parameter
    coroutineScope: CoroutineScope // Add coroutineScope parameter
) {
    var showSnakeGame by remember { mutableStateOf(false) } // State to control game visibility
    
    Scaffold(
        topBar = {
            // Only show TopAppBar if not showing the game, or let SnakeGameScreen handle its own
            if (!showSnakeGame) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
            // If showSnakeGame is true, SnakeGameScreen will provide its own TopAppBar
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (showSnakeGame) {
            // Show Snake Game
            SnakeGameScreen(onBack = { showSnakeGame = false }) // Pass lambda to hide game
        } else {
            // Show Settings Menu
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SettingsMenuItem(
                    title = "Security Settings",
                    icon = Icons.Default.Security,
                    onClick = {
                        navController.navigate(XianDestinations.SETTINGS_SECURITY)
                    }
                )

                SettingsMenuItem(
                    title = "Network Settings",
                    icon = Icons.Default.NetworkCheck,
                    onClick = {
                        navController.navigate(XianDestinations.SETTINGS_NETWORK)
                    }
                )

                SettingsMenuItem(
                    title = "Snake Game",
    icon = Icons.Default.VideogameAsset,
    onClick = {
        showSnakeGame = true // Show the game instead of navigating
    }
)

SettingsMenuItem(
    title = "XIAN WALLET APP News",
    icon = Icons.Default.Send, // Changed icon to Send (placeholder for Telegram)
    onClick = { /* TODO: Implement navigation or action */ }
)

SettingsMenuItem(
    title = "Rate Us",
    icon = Icons.Default.Star,
    onClick = { /* TODO: Implement navigation or action */ }
)

Spacer(modifier = Modifier.weight(1f)) // Pushes the logo to the bottom

Image(
    painter = painterResource(id = R.drawable.xw), // Changed logo to xw.png
    contentDescription = "XIAN WALLET APP Logo",
    modifier = Modifier
        .size(64.dp) // Adjust size as needed
        .padding(bottom = 8.dp)
)
// Removed the Text element below the logo as requested
Spacer(modifier = Modifier.height(16.dp)) // Add some padding at the very bottom

                // TODO: Add other settings categories if needed
            }
        }
        // Dialogs are now handled within their respective sub-screens (SecuritySettingsScreen, NetworkSettingsScreen)
    }
}

// Keep the SettingsMenuItem composable definition here
@Composable
fun SettingsMenuItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp) // Add vertical padding between cards
            .clickable { onClick() }, // Make the whole card clickable
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Match TokenItem background
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp), // Adjust padding inside the card
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween // Keep arrangement
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = icon, // Use the provided icon
                contentDescription = title, // Use title for accessibility
                tint = MaterialTheme.colorScheme.primary
            )
            // Optional: Add a chevron icon to indicate navigation
            // Icon(
            //     imageVector = Icons.Default.ChevronRight,
            //     contentDescription = "Navigate",
            //     tint = MaterialTheme.colorScheme.onSurfaceVariant
            // )
        }
    }
}