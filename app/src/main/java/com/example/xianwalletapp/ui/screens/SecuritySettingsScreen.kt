package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import java.io.IOException
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.wallet.WalletManager
import com.example.xianwalletapp.ui.theme.XianButtonType
import com.example.xianwalletapp.ui.theme.xianButtonColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavController,
    walletManager: WalletManager,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope // Pass CoroutineScope for launching coroutines
) {
    var showDeleteWalletDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requirePasswordOnStartup by remember { mutableStateOf(walletManager.getRequirePassword()) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Settings") },
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
            // Require password on startup toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = "Password Protection",
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Require Password on Startup",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Ask for password every time the app is launched",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = requirePasswordOnStartup,
                    onCheckedChange = {
                        requirePasswordOnStartup = it
                        walletManager.setRequirePassword(it)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Password requirement on startup ${if (it) "enabled" else "disabled"}")
                        }
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Backup private key button
            Button(
                onClick = { showBackupDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = xianButtonColors(XianButtonType.SECONDARY)
            ) {
                Icon(
                    Icons.Default.Backup,
                    contentDescription = "Backup",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Backup Private Key")
            }

            // Delete wallet button
            Button(
                onClick = { showDeleteWalletDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Delete Wallet")
            }
        }

        // Delete wallet confirmation dialog
        if (showDeleteWalletDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteWalletDialog = false },
                title = { Text("Delete Wallet") },
                text = {
                    Text("Are you sure you want to delete your wallet? This action cannot be undone and you will lose access to your funds unless you have backed up your private key.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            walletManager.deleteWallet()
                            showDeleteWalletDialog = false
                            // Navigate back to welcome screen
                            navController.navigate(XianDestinations.WELCOME) {
                                popUpTo(XianDestinations.WALLET) { inclusive = true } // Assuming WALLET is the main screen route
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteWalletDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Backup private key dialog
        if (showBackupDialog) {
            var privateKeyText by remember { mutableStateOf("") }
            var passwordVerified by remember { mutableStateOf(false) }

            // Define the launcher inside the scope where context, coroutineScope, etc. are available
            val createFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain"),
                onResult = { uri: Uri? ->
                    if (uri != null) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                if (privateKeyText.isNotEmpty()) {
                                    outputStream.write(privateKeyText.toByteArray())
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Private key exported successfully")
                                    }
                                } else {
                                     coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Error: Private key is empty, cannot export")
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Error exporting private key: ${e.message}")
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Export cancelled")
                        }
                    }
                }
            )

            AlertDialog(
                onDismissRequest = {
                    showBackupDialog = false
                    password = ""
                    privateKeyText = ""
                    passwordVerified = false
                    errorMessage = null
                },
                title = { Text("Backup Private Key") },
                text = {
                    Column {
                        if (!passwordVerified) {
                            Text("Enter your password to view your private key")
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; errorMessage = null },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val privateKey = walletManager.getPrivateKey(password)
                                    if (privateKey != null) {
                                        // Convert private key bytes to hex string
                                        privateKeyText = privateKey.joinToString("") {
                                            "%02x".format(it)
                                        }
                                        passwordVerified = true
                                        errorMessage = null
                                    } else {
                                        errorMessage = "Invalid password"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = xianButtonColors(XianButtonType.PRIMARY)
                            ) {
                                Text("Verify Password")
                            }
                        } else {
                            Text("This is your private key. Keep it safe and never share it with anyone.")
                            Spacer(modifier = Modifier.height(16.dp))

                            // Display private key in a bordered box
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = privateKeyText,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Buttons for Copy and Export
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(privateKeyText))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Private key copied to clipboard")
                                        }
                                    },
                                    colors = xianButtonColors(XianButtonType.SECONDARY)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text("Copy")
                                }

                                Button(
                                    onClick = {
                                        // Launch the SAF file creator
                                        createFileLauncher.launch("xian_private_key.txt")
                                    },
                                    colors = xianButtonColors(XianButtonType.SECONDARY)
                                ) {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = "Export",
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text("Export")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Warning: Anyone with your private key has full access to your wallet and funds.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBackupDialog = false
                            password = ""
                            privateKeyText = ""
                            passwordVerified = false
                            errorMessage = null
                        }
                    ) {
                        Text(if (passwordVerified) "Done" else "Cancel")
                    }
                }
            )
        }
    }
}