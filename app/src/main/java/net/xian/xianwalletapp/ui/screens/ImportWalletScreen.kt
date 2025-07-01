package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors

/**
 * Screen for importing an existing wallet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(navController: NavController, walletManager: WalletManager) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) } // 0 for Phrase, 1 for Private Key
    val importTypes = listOf("Recovery Phrase", "Private Key")

    var mnemonicPhrase by remember { mutableStateOf("") }
    var privateKeyHex by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var importedPublicKey by remember { mutableStateOf("") }
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Import Existing Wallet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            // Import Type Selector
            TabRow(selectedTabIndex = selectedTabIndex) {
                importTypes.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index; errorMessage = null },
                        text = { Text(title) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Conditional Input Field
            when (selectedTabIndex) {
                0 -> { // Recovery Phrase
                    OutlinedTextField(
                        value = mnemonicPhrase,
                        onValueChange = { mnemonicPhrase = it.lowercase(); errorMessage = null }, // Convert to lowercase for consistency
                        label = { Text("Recovery Phrase (24 words)") },
                        placeholder = { Text("Enter words separated by spaces") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .heightIn(min = 100.dp), // Allow more space for phrase
                        maxLines = 4 // Allow multiple lines
                    )
                }
                1 -> { // Private Key
                    OutlinedTextField(
                        value = privateKeyHex,
                        onValueChange = { privateKeyHex = it; errorMessage = null },
                        label = { Text("Private Key (Hex)") },
                        placeholder = { Text("Enter your private key hex string") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii) // Allow hex chars
                    )
                }
            }
            
            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("New Password") },
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
            
            // Import wallet button
            Button(
                onClick = {
                    // Clear previous error
                    errorMessage = null

                    // Basic Password Validation (common to both)
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (password != confirmPassword) {
                        errorMessage = "Passwords do not match"
                        return@Button
                    }

                    isLoading = true
                    val result = when (selectedTabIndex) {
                        0 -> { // Import via Mnemonic Phrase
                            if (mnemonicPhrase.isBlank()) {
                                errorMessage = "Recovery phrase cannot be empty"
                                isLoading = false
                                null // Indicate validation failure
                            } else {
                                // Basic word count check (more robust check in WalletManager)
                                if (mnemonicPhrase.trim().split("\\s+".toRegex()).size != 24) {
                                    errorMessage = "Please enter exactly 24 words"
                                    isLoading = false
                                    null // Indicate validation failure
                                } else {
                                    walletManager.importWalletFromMnemonic(mnemonicPhrase, password)
                                }
                            }
                        }
                        1 -> { // Import via Private Key
                            if (privateKeyHex.isBlank()) {
                                errorMessage = "Private key cannot be empty"
                                isLoading = false
                                null // Indicate validation failure
                            } else if (!privateKeyHex.matches(Regex("^[a-fA-F0-9]{64}$"))) { // Basic hex validation
                                errorMessage = "Invalid private key format (must be 64 hex characters)"
                                isLoading = false
                                null // Indicate validation failure
                            } else {
                                walletManager.importWallet(privateKeyHex, password)
                            }
                        }
                        else -> { // Should not happen
                            errorMessage = "Invalid import type selected"
                            isLoading = false
                            null
                        }
                    }
                    isLoading = false // Ensure loading is stopped even if result is null

                    if (result != null) {
                        if (result.success) {
                            importedPublicKey = result.publicKey ?: ""
                            showSuccessDialog = true
                        } else {
                            errorMessage = result.error ?: "Failed to import wallet"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = xianButtonColors(XianButtonType.SECONDARY),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Import Wallet", fontSize = 16.sp)
                }
            }
        }
        
        // Success dialog
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Wallet Imported") },
                text = {
                    Column {
                        Text("Your wallet has been imported successfully!")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your public address:")
                        Text(
                            text = importedPublicKey,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            navController.navigate(XianDestinations.WALLET) {
                                popUpTo(XianDestinations.WELCOME) { inclusive = true }
                            }
                        }
                    ) {
                        Text("Continue")
                    }
                }
            )
        }
    }
}