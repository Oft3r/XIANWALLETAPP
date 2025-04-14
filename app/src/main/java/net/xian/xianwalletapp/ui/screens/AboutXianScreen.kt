package net.xian.xianwalletapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Import all filled icons for simplicity, or list individually
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Import Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Removed NavController import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutXianScreen(onBack: () -> Unit) { // Changed parameter from navController to onBack lambda
    val context = LocalContext.current

    // Helper function to open URLs (kept from previous version)
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
             println("Could not open URL: $url, Error: ${e.message}")
        }
    }

    // Note: Scaffold is used here, which might conflict slightly with the parent Scaffold in SettingsScreen.
    // If visual glitches occur, we might need to remove this Scaffold and pass paddingValues down.
    // For now, keeping it for self-contained structure.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About XIAN Blockchain") }, // Revert color change here
                navigationIcon = {
                    IconButton(onClick = onBack) { // Use the onBack lambda here
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) { // Main column to hold Card and Icons Row
            // Card Section
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Welcome to XIAN!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Yellow
                        )
                        Text(
                            text = "Xian is a live Layer 1 blockchain where smart contracts run in pure Python (no Solidity needed). If you're a builder, dev, or just curious, you're in the right place.",
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp
                        )
                        Text(
                            text = "Explore the Ecosystem:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClickableLink(text = "🔹 Main site: ", url = "https://xian.org")
                            ClickableLink(text = "🔹 Docs: ", url = "https://docs.xian.org")
                            ClickableLink(text = "🔹 Explorer: ", url = "https://explorer.xian.org")
                            ClickableLink(text = "🔹 Bridge: ", url = "https://bridge.xian.org")
                            ClickableLink(text = "🔹 DEX: ", url = "https://snakexchange.org/pairs")
                        }
                    } // End Column inside Card
                } // End Card
            } // End Box for Card padding

            Spacer(modifier = Modifier.height(16.dp)) // Add space between Card and Icons

            // Social Icons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, // Distribute icons evenly
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Using placeholder icons. Replace with actual brand icons if available/added.
                SocialIconButton(url = "https://t.me/xian_network/", icon = Icons.Default.Send, description = "Telegram")
                SocialIconButton(url = "https://x.com/xian_network/", icon = Icons.Default.Close, description = "X/Twitter") // Placeholder for X
                SocialIconButton(url = "https://github.com/xian-network/", icon = Icons.Default.Code, description = "GitHub") // Placeholder for GitHub
                SocialIconButton(url = "https://discord.gg/nWQ4sPZXr5", icon = Icons.Default.Chat, description = "Discord") // Placeholder for Discord
                SocialIconButton(url = "https://xiannetwork.medium.com/", icon = Icons.Default.Article, description = "Medium") // Placeholder for Medium
                SocialIconButton(url = "https://www.reddit.com/r/xiannetwork", icon = Icons.Default.Group, description = "Reddit") // Changed placeholder icon
            }

            Spacer(modifier = Modifier.height(16.dp)) // Add padding at the bottom
        } // End Main Column
    }
}

// Using the simplified ClickableLink from the previous attempt
@Composable
fun ClickableLink(text: String, url: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    println("Could not open URL: $url, Error: ${e.message}")
                }
            }
        )
    }
}

// Helper composable for Social Icons
@Composable
fun SocialIconButton(url: String, icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    val context = LocalContext.current
    IconButton(onClick = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            println("Could not open URL: $url, Error: ${e.message}")
        }
    }) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary // Optional: Tint icons
        )
    }
}