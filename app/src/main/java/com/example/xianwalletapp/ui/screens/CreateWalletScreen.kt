package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.wallet.WalletManager
import com.example.xianwalletapp.ui.theme.XianButtonType
import com.example.xianwalletapp.ui.theme.xianButtonColors

/**
 * Screen for creating a new wallet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(navController: NavController, walletManager: WalletManager) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showMnemonicDialog by remember { mutableStateOf(false) } // Renamed state
    var newPublicKey by remember { mutableStateOf("") }
    var newMnemonicPhrase by remember { mutableStateOf("") } // Added state for mnemonic
    var mnemonicAcknowledged by remember { mutableStateOf(false) } // State for backup confirmation
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Create New Wallet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            
            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            
            // Confirm password field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text("Confirm Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            
            // Password requirements
            Text(
                text = "Password must be at least 6 characters",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Create wallet button
            Button(
                onClick = {
                    // Validate password
                    when {
                        password.length < 6 -> {
                            errorMessage = "Password must be at least 6 characters"
                        }
                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match"
                        }
                        else -> {
                            isLoading = true
                            // Create wallet
                            val result = walletManager.createWallet(password)
                            isLoading = false
                            
                            if (result.success) {
                                newPublicKey = result.publicKey ?: ""
                                newMnemonicPhrase = result.mnemonicPhrase ?: "" // Store mnemonic
                                mnemonicAcknowledged = false // Reset acknowledgment
                                showMnemonicDialog = true // Show the mnemonic dialog
                            } else {
                                errorMessage = result.error ?: "Failed to create wallet"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = xianButtonColors(XianButtonType.PRIMARY),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Wallet", fontSize = 16.sp)
                }
            }
        }
        
        // Success dialog
        // Mnemonic Backup Dialog
        if (showMnemonicDialog) {
            AlertDialog(
                onDismissRequest = { /* Prevent dismissing without acknowledging */ },
                title = { Text("IMPORTANT: Backup Your Phrase!") },
                text = {
                    Column {
                        Text(
                            "Your 24-word recovery phrase is the ONLY way to restore your wallet if you lose your device or uninstall the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error // Emphasize importance
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Write these words down in the correct order and store them in a secure, offline location. Never share them or store them digitally.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Display the phrase (Consider better formatting for readability)
                        Text(
                            text = newMnemonicPhrase,
                            style = MaterialTheme.typography.bodyLarge, // Make phrase stand out
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = mnemonicAcknowledged,
                                onCheckedChange = { mnemonicAcknowledged = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I have securely backed up my recovery phrase.")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showMnemonicDialog = false
                            navController.navigate(XianDestinations.WALLET) {
                                popUpTo(XianDestinations.WELCOME) { inclusive = true }
                            }
                        },
                        enabled = mnemonicAcknowledged, // Enable button only after acknowledgment
                        colors = xianButtonColors(XianButtonType.PRIMARY)
                    ) {
                        Text("Continue to Wallet")
                    }
                },
                dismissButton = null // No dismiss button to force acknowledgment
            )
        }
    }
}