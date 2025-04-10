package com.example.xianwalletapp.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext // Import LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle // Added import
// import androidx.compose.material.icons.filled.ArrowBack // Deprecated
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use AutoMirrored
import androidx.compose.material.icons.automirrored.filled.Send // Use AutoMirrored
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.NetworkCheck
// import androidx.compose.material.icons.filled.ChevronRight // Optional, can be added later if needed
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit // Import Edit icon
import androidx.compose.material.icons.filled.Delete // Import Delete icon
// import androidx.compose.material.icons.filled.Send // Deprecated
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp // Import for expanded state
import androidx.compose.material.icons.filled.Add
// import androidx.compose.material.DropdownMenu // No longer needed
// import androidx.compose.material.DropdownMenuItem // No longer needed
import androidx.compose.material3.HorizontalDivider // Use HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.* // Import remember, mutableStateOf, etc.
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import for state collection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Import for sp unit
import coil.compose.AsyncImage // For Coil image loading
import com.example.xianwalletapp.R // Import R for drawable resources
import androidx.navigation.NavController
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.network.XianNetworkService
// Removed NftInfo import as it's no longer needed here
import com.example.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.CoroutineScope // Add CoroutineScope import
import kotlinx.coroutines.launch

// Helper function moved outside composables
private fun summarizeAddress(address: String?): String {
    return if (address != null && address.length > 6) {
        "${address.take(3)}...${address.takeLast(3)}"
    } else {
        address ?: "N/A" // Show N/A if address is null or too short
    }
}

// Composable for the Rename Wallet Dialog
@Composable
fun RenameWalletDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) } // Pre-fill with current name
    var showError by remember { mutableStateOf(false) } // State to show error if name is blank

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Wallet") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        showError = it.isBlank() // Show error immediately if blank
                     },
                    label = { Text("New Wallet Name") },
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Text(
                        text = "Wallet name cannot be empty.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName.trim())
                    } else {
                        showError = true // Ensure error is shown on confirm click if blank
                    }
                }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Composable for the Delete Wallet Confirmation Dialog
@Composable
fun DeleteConfirmationDialog(
    walletName: String?, // Display the name for clarity
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Wallet?") },
        text = {
            Text("Are you sure you want to delete '${walletName ?: "this wallet"}'? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) // Use error color for confirm
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


// Updated Expandable Wallet Header Card
@Composable
fun ExpandableWalletHeaderCard(
    preferredNftContract: String?,
    activeWalletAddress: String?,
    activeWalletName: String?, // Add parameter for the active wallet's name
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    walletKeys: Set<String>,
    walletManager: WalletManager, // Pass WalletManager to get names for the list
    onWalletSelected: (String) -> Unit,
    onAddWalletClicked: () -> Unit,
    onEditWalletClicked: (String) -> Unit, // Add callback for edit
    onDeleteWalletClicked: (String) -> Unit // Add callback for delete
) {
    // val imageUrl = preferredNftContract?.let { "https://pixelsnek.xian.org/gif/${it}.gif" } // No longer needed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .animateContentSize(), // Add animation for expand/collapse
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Match other cards
        )
    ) {
        Column { // Use Column to stack header and expandable content
            // Header Row (Clickable to toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() } // Click row to toggle
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image( // Use static app logo
                    painter = painterResource(id = R.drawable.xwallet), // Use xwallet.jpg
                    contentDescription = "XWallet Logo", // Updated description
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer) // Keep background for consistency
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) { // Allow text column to take available space
                    // Display the active wallet's name, fallback to "My Wallet"
                    Text(
                        text = activeWalletName ?: "My Wallet", // Use the passed name
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summarizeAddress(activeWalletAddress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expand/Collapse Icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse Wallets" else "Expand Wallets",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Expandable Content
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    walletKeys.forEach { key ->
                        // Get the name for this specific key
                        val walletName = walletManager.getWalletName(key) ?: "Wallet" // Fallback name
                        val isCurrent = key == activeWalletAddress

                        Row( // Change to Row to place icons at the end
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWalletSelected(key) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically // Align items vertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) { // Column for text, takes available space
                                Text(
                                    text = walletName,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, // Name slightly bolder
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    fontSize = 15.sp // Slightly larger name font
                                )
                                Text(
                                    text = summarizeAddress(key),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, // Subdued address color
                                    modifier = Modifier.padding(start = 4.dp) // Indent address slightly
                                )
                            }

                            // Edit Icon Button
                            IconButton(onClick = { onEditWalletClicked(key) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Wallet Name",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant // Subtle color
                                )
                            }

                            // Delete Icon Button
                            IconButton(onClick = { onDeleteWalletClicked(key) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Wallet",
                                    tint = MaterialTheme.colorScheme.error // Error color for delete
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Add Wallet Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddWalletClicked() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Wallet",
                            tint = MaterialTheme.colorScheme.primary // Match icon tint
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Wallet", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}


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
    // Removed preferredNftContract and walletAddress parameters
    // They are now collected from the WalletManager's flow
) {
    var showSnakeGame by remember { mutableStateOf(false) } // State to control game visibility
    var showAboutXian by remember { mutableStateOf(false) } // State to control About Xian visibility
    var isWalletSectionExpanded by remember { mutableStateOf(false) } // Renamed state for clarity
    val context = LocalContext.current // Get context here

    // State for Rename Wallet Dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var walletToRenameKey by remember { mutableStateOf<String?>(null) }
    var currentWalletNameForDialog by remember { mutableStateOf("") } // Use a distinct name for dialog state

    // State for Delete Wallet Confirmation Dialog
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var walletToDeleteKey by remember { mutableStateOf<String?>(null) }

    var renameTrigger by remember { mutableStateOf(0) } // Trigger for recomposing name

    // Collect the active wallet public key from the WalletManager's flow
    val activePublicKey by walletManager.activeWalletPublicKeyFlow.collectAsStateWithLifecycle()

    // Get the preferred NFT contract based on the current active public key
    val preferredNftContract = remember(activePublicKey) {
        walletManager.getPreferredNftContract()
    }
    // Get the active wallet name, triggered by key change or rename
    val activeWalletName = remember(activePublicKey, renameTrigger) {
        walletManager.getActiveWalletName()
    }

    Scaffold(
        topBar = {
            if (!showSnakeGame && !showAboutXian) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        // Conditionally display the Rename Wallet Dialog
        if (showRenameDialog && walletToRenameKey != null) {
            RenameWalletDialog(
                currentName = currentWalletNameForDialog, // Pass the captured current name
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    val success = walletManager.renameWallet(walletToRenameKey!!, newName)
                    if (success) {
                        renameTrigger++ // Increment trigger on success
                    }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "Wallet renamed successfully!" else "Failed to rename wallet."
                        )
                    }
                    showRenameDialog = false
                    // Note: UI should now update due to renameTrigger change
                    // For now, relying on recomposition from state change
                }
            )
        }

        // Conditionally display the Delete Confirmation Dialog
        if (showDeleteConfirmDialog && walletToDeleteKey != null) {
            DeleteConfirmationDialog(
                walletName = walletManager.getWalletName(walletToDeleteKey!!), // Get name for display
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirm = {
                    val success = walletManager.deleteWallet(walletToDeleteKey!!)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) "Wallet deleted successfully!" else "Failed to delete wallet."
                        )
                    }
                    showDeleteConfirmDialog = false
                    // The active wallet might change, state flows should handle UI updates
                }
            )
        }


        when {
            showAboutXian -> {
                AboutXianScreen(onBack = { showAboutXian = false })
            }
            showSnakeGame -> {
                SnakeGameScreen(onBack = { showSnakeGame = false })
            }
            else -> {
                // Show Settings Menu
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Use the new ExpandableWalletHeaderCard
                    ExpandableWalletHeaderCard(
                        preferredNftContract = preferredNftContract,
                        activeWalletAddress = activePublicKey,
                        activeWalletName = activeWalletName, // Pass the name
                        isExpanded = isWalletSectionExpanded, // Use the renamed state variable
                        onToggleExpand = { isWalletSectionExpanded = !isWalletSectionExpanded }, // Toggle function
                        walletKeys = walletManager.getWalletPublicKeys(), // Pass the keys
                        walletManager = walletManager, // Pass walletManager instance
                        onWalletSelected = { selectedKey ->
                            walletManager.setActiveWallet(selectedKey)
                            isWalletSectionExpanded = false // Collapse after selection
                        },
                        onAddWalletClicked = {
                            isWalletSectionExpanded = false // Collapse before navigating
                            navController.navigate(XianDestinations.WELCOME)
                        },
                        onEditWalletClicked = { walletKey ->
                            // Set state for the dialog
                            walletToRenameKey = walletKey
                            currentWalletNameForDialog = walletManager.getWalletName(walletKey) ?: "Wallet" // Get current name for dialog
                            showRenameDialog = true // Show the dialog
                        },
                        onDeleteWalletClicked = { walletKey ->
                            // Set state for the delete confirmation dialog
                            walletToDeleteKey = walletKey
                            showDeleteConfirmDialog = true // Show the dialog
                        }
                    )

                    // Spacer below the Expandable Card
                    Spacer(modifier = Modifier.height(16.dp))

                    // Rest of the SettingsMenuItems...
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
                        icon = Icons.AutoMirrored.Filled.Send, // Use AutoMirrored Send icon
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/xianwapp"))
                            try { // Add try-catch block for safety
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                coroutineScope.launch { // Use the provided coroutineScope
                                    snackbarHostState.showSnackbar("Could not open link. Is Telegram installed?")
                                }
                            }
                        }
                    )

                    SettingsMenuItem(
                        title = "About XIAN Blockchain",
                        icon = Icons.Default.Info, // Using Info icon
                        onClick = {
                            showAboutXian = true // Show the About screen conditionally
                        }
                    )


                    SettingsMenuItem(
                        title = "Rate Us",
                        icon = Icons.Default.Star,
                        onClick = { /* TODO: Implement navigation or action */ }
                    )

                    Spacer(Modifier.weight(1f)) // Correct syntax

                    Image(
                        painter = painterResource(id = R.drawable.xw), // Changed logo to xw.png
                        contentDescription = "XIAN WALLET APP Logo",
                        modifier = Modifier
                            .size(64.dp) // Adjust size as needed
                            .padding(bottom = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp)) // Add some padding at the very bottom

                } // End of main settings Column
            } // End of else block
        } // End of when block
    } // End Scaffold
} // End SettingsScreen composable

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
        }
    }
}