package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope // Pass CoroutineScope for launching coroutines
) {
    var rpcUrl by remember { mutableStateOf(walletManager.getRpcUrl()) }
    var explorerUrl by remember { mutableStateOf(walletManager.getExplorerUrl()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "Network Settings",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) } // Use the passed snackbarHostState
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // RPC URL field
            OutlinedTextField(
                value = rpcUrl,
                onValueChange = { rpcUrl = it },
                label = { Text("RPC URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            // Explorer URL field
            OutlinedTextField(
                value = explorerUrl,
                onValueChange = { explorerUrl = it },
                label = { Text("Explorer URL") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                singleLine = true
            )

            // Save button
            Button(
                onClick = {
                    showSaveDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = xianButtonColors(XianButtonType.PRIMARY)
            ) {
                Text("Save Network Settings")
            }

            // Reset button
            Button(
                onClick = {
                    showResetDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = xianButtonColors(XianButtonType.SECONDARY)
            ) {
                Text("Reset to Default")
            }
        }

        // Save confirmation dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Settings") },
                text = { Text("Are you sure you want to save these network settings? The app will need to reconnect to the network.") },
                confirmButton = {
                    Button(
                        onClick = {
                            walletManager.setRpcUrl(rpcUrl)
                            walletManager.setExplorerUrl(explorerUrl)
                            networkService.setRpcUrl(rpcUrl)
                            networkService.setExplorerUrl(explorerUrl)
                            showSaveDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Settings saved")
                            }
                        },
                        colors = xianButtonColors(XianButtonType.PRIMARY)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSaveDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Reset confirmation dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Settings") },
                text = { Text("Are you sure you want to reset network settings to default values?") },
                confirmButton = {
                    Button(
                        onClick = {
                            rpcUrl = "https://node.xian.org" // Default RPC
                            explorerUrl = "https://explorer.xian.org" // Default Explorer
                            // Note: We only update the local state here. User needs to click Save to persist.
                            showResetDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Fields reset to default. Click Save to apply.")
                            }
                        },
                        colors = xianButtonColors(XianButtonType.SECONDARY)
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}