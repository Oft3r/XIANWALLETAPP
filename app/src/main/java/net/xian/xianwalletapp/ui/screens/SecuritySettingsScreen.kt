package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.io.IOException
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors
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
    var password by remember { mutableStateOf("") } // For backup dialog
    var errorMessage by remember { mutableStateOf<String?>(null) } // For backup dialog
    var requirePasswordOnStartup by remember { mutableStateOf(walletManager.getRequirePassword()) }
    var biometricEnabled by remember { mutableStateOf(walletManager.isBiometricEnabled()) }
    val context = LocalContext.current
    val biometricManager = BiometricManager.from(context)
    val canUseBiometrics = remember { biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS }
    val clipboardManager = LocalClipboardManager.current
    var showEnableBiometricPasswordDialog by remember { mutableStateOf(false) }
    var passwordToEnableBiometrics by remember { mutableStateOf("") } // Temp storage for password during enable flow
    var showPasswordRequiredDialog by remember { mutableStateOf(false) } // Dialog for biometric prerequisite

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
                            text = "Security Settings",
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    onCheckedChange = { isChecked ->
                        requirePasswordOnStartup = isChecked
                        walletManager.setRequirePassword(isChecked)

                        // New logic: If password requirement is disabled, also disable biometrics if it was enabled
                        if (!isChecked && biometricEnabled) {
                            try {
                                walletManager.disableBiometric()
                                biometricEnabled = false // Update biometric state
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Biometric unlock disabled as password requirement was turned off.")
                                }
                            } catch (e: Exception) {
                                // Handle potential error during biometric disable
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error disabling biometrics: ${e.message}")
                                }
                                // Optional: Revert requirePasswordOnStartup state if disabling biometrics fails critically?
                                // For now, just log the error and proceed with password setting change.
                            }
                        } else {
                            // Show standard snackbar for password requirement change
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Password requirement on startup ${if (isChecked) "enabled" else "disabled"}")
                            }
                        }
                    }
                )
            }


            // Biometric unlock toggle (only show if available)
            if (canUseBiometrics) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "Biometric Unlock",
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Biometric Unlock",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Use fingerprint or face unlock when available",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) { // User wants to enable
                                if (requirePasswordOnStartup) {
                                    // Password requirement is met, proceed with enabling biometrics
                                    showEnableBiometricPasswordDialog = true
                                    // Don't set biometricEnabled = true yet, wait for full process
                                } else {
                                    // Password requirement NOT met, show prerequisite dialog
                                    showPasswordRequiredDialog = true
                                    // Keep the switch visually off by not changing biometricEnabled state here
                                }
                            } else {
                                // Disabling biometrics
                                try {
                                    walletManager.disableBiometric()
                                    biometricEnabled = false // Update state only after successful disable
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Biometric unlock disabled")
                                    }
                                } catch (e: Exception) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Error disabling biometrics: ${e.message}")
                                    }
                                    // Keep the toggle visually enabled if disabling fails
                                    // Revert the state change visually if disable fails
                                    // This requires recomposition, which Switch should trigger
                                    // We might need to force recomposition if it doesn't update automatically
                                    // For now, just log and show snackbar. The state 'biometricEnabled'
                                    // should ideally be updated based on walletManager.isBiometricEnabled()
                                    // after the operation attempt. Let's keep it simple for now.
                                    // biometricEnabled = true // Re-setting might cause issues if recomposition is tricky
                                }
                            }
                        }
                    )
                }
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
                            val activePublicKey = walletManager.getActiveWalletPublicKey()
                            if (activePublicKey != null) {
                                walletManager.deleteWallet(publicKeyToDelete = activePublicKey)
                            } else {
                                // Handle error: Cannot delete if no active wallet is found (should not happen here)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error: Could not identify wallet to delete.")
                                }
                            }
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


        // --- Biometric Prompt Setup (for enabling) ---
        val view = LocalView.current
        val activity = remember(view) { view.context as? FragmentActivity }
        val executor = ContextCompat.getMainExecutor(context)
        // Initialize BiometricPrompt only if activity is available
        // Initialize BiometricPrompt only if activity is available
        val biometricPromptEnable = remember(activity, executor) {
            activity?.let { act -> // Only create prompt if activity is not null
                BiometricPrompt(act, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Biometric prompt error: $errString")
                            }
                            biometricEnabled = false // Reset state
                            passwordToEnableBiometrics = ""
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            result.cryptoObject?.cipher?.let { cipher ->
                                if (walletManager.finalizeBiometricEnable(passwordToEnableBiometrics, cipher)) {
                                    biometricEnabled = true
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Biometric unlock enabled successfully.") }
                                } else {
                                    biometricEnabled = false
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Failed to finalize biometric setup.") }
                                }
                            } ?: run {
                                 biometricEnabled = false
                                 coroutineScope.launch { snackbarHostState.showSnackbar("Biometric error: Crypto object missing.") }
                            }
                            passwordToEnableBiometrics = ""
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            coroutineScope.launch { snackbarHostState.showSnackbar("Biometric authentication failed.") }
                            biometricEnabled = false // Reset state
                            passwordToEnableBiometrics = ""
                        }
                    })
            } // Returns null if activity is null
        }

        val promptInfoEnable = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm Biometric Setup")
            .setSubtitle("Authenticate to finish enabling biometric unlock")
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false) // Can be true if you want explicit confirmation
            .build()


        // Enable Biometric - Step 1: Password Dialog
        if (showEnableBiometricPasswordDialog) {
            var enablePassword by remember { mutableStateOf("") }
            var enableError by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = {
                    showEnableBiometricPasswordDialog = false
                    biometricEnabled = false // Reset toggle if dialog is cancelled
                 },
                title = { Text("Verify Password") },
                text = {
                    Column {
                        Text("Enter your current wallet password to proceed with enabling biometric unlock.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = enablePassword,
                            onValueChange = { enablePassword = it; enableError = null },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (enableError != null) {
                            Text(
                                text = enableError!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // 1. Verify password
                            val checkKey = walletManager.getPrivateKey(enablePassword)


                            if (checkKey != null) {
                                // Password correct - Proceed to Step 2: Biometric Prompt
                                walletManager.clearPrivateKeyCache() // Clear cache after verification
                                passwordToEnableBiometrics = enablePassword // Store password temporarily
                                showEnableBiometricPasswordDialog = false // Close password dialog

                                // Prepare cipher and show biometric prompt
                                val cipher = walletManager.prepareBiometricEncryption()
                                // Ensure prompt is not null before authenticating
                                if (cipher != null && biometricPromptEnable != null) {
                                    biometricPromptEnable.authenticate(promptInfoEnable, BiometricPrompt.CryptoObject(cipher))
                                } else {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Error preparing biometric setup or prompt unavailable.") }
                                    biometricEnabled = false // Reset toggle if preparation fails or prompt is null
                                    passwordToEnableBiometrics = "" // Clear password
                                }

                            } else {
                                // Password incorrect
                                enableError = "Invalid password"
                            }
                        }
                    ) {
                        Text("Verify")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showEnableBiometricPasswordDialog = false
                        biometricEnabled = false // Reset toggle if dialog is cancelled
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog to inform user password requirement is needed for biometrics
        if (showPasswordRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordRequiredDialog = false },
                title = { Text("Password Required") },
                text = { Text("You must enable 'Require Password on Startup' before enabling biometric unlock.") },
                confirmButton = {
                    TextButton(onClick = { showPasswordRequiredDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

    }
}
