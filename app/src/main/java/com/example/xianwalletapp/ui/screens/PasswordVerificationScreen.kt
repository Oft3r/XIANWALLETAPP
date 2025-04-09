package com.example.xianwalletapp.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.wallet.WalletManager
import com.example.xianwalletapp.ui.theme.XianButtonType
import com.example.xianwalletapp.ui.theme.xianButtonColors
import kotlinx.coroutines.launch

/**
 * Password verification screen shown at startup when password protection is enabled
 * Supports biometric authentication if enabled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordVerificationScreen(
    navController: NavController,
    walletManager: WalletManager,
    onPasswordVerified: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalContext.current as FragmentActivity // Get FragmentActivity for BiometricPrompt
    val executor = ContextCompat.getMainExecutor(context)
    var canAuthenticateWithBiometrics by remember { mutableStateOf(false) }
    var showBiometricPrompt by remember { mutableStateOf(false) }
    val biometricManager = BiometricManager.from(context)

    // --- Biometric Prompt Setup ---
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Handle error, e.g., show a Snackbar or Toast
                // Don't call onPasswordVerified() here
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Biometric authentication error: $errString")
                }
                showBiometricPrompt = false // Hide prompt state if error occurs
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                showBiometricPrompt = false // Hide prompt state first
                result.cryptoObject?.cipher?.let { cipher ->
                    // Use the cipher to decrypt the password and unlock the wallet
                    if (walletManager.unlockWalletWithBiometricCipher(cipher)) {
                        // Wallet unlocked successfully, proceed
                        onPasswordVerified()
                    } else {
                        // Failed to unlock wallet even after successful biometric auth (e.g., Keystore issue)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Failed to unlock wallet after biometric authentication.")
                        }
                    }
                } ?: run {
                    // CryptoObject or Cipher was null - should not happen with correct setup
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Biometric authentication error: Crypto object missing.")
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Handle failure (biometric recognized but not valid)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Biometric authentication failed.")
                }
                showBiometricPrompt = false // Hide prompt state on failure
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Unlock")
        .setSubtitle("Log in using your biometric credential")
        // Allow device credential fallback (PIN, pattern, password) if desired
        // .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .setNegativeButtonText("Use Password") // User can cancel and use password
        .setConfirmationRequired(false) // Optional: require explicit confirmation
        .build()

    // --- Check Biometric Availability and Trigger Prompt --- 
    LaunchedEffect(Unit) {
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                canAuthenticateWithBiometrics = true
                // Check if user enabled biometrics in app settings
                if (walletManager.isBiometricEnabled()) { // Assuming this method exists
                    showBiometricPrompt = true
                }
            }
            else -> {
                canAuthenticateWithBiometrics = false
                // Optionally inform user why biometrics aren't available
            }
        }
    }

    // Show the prompt when the state is true
    LaunchedEffect(showBiometricPrompt) {
        if (showBiometricPrompt) {
            // Get the cipher for decryption before showing the prompt
            val cipher = walletManager.getBiometricCipherForDecryption()
            if (cipher != null) {
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            } else {
                // Handle error: Keystore key might be missing or invalid
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Error preparing biometric prompt. Please try password.")
                }
                showBiometricPrompt = false // Don't show prompt if cipher failed
            }
            // Reset the trigger state immediately after attempting authentication
            // showBiometricPrompt = false // Let the callback handle hiding based on result
        }
    }
    
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Unlock Your Wallet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
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
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Unlock button
            Button(
                onClick = {
                    if (password.isBlank()) {
                        errorMessage = "Please enter your password"
                        return@Button
                    }
                    
                    isLoading = true
                    // Verify password by trying to unlock the wallet
                    val privateKey = walletManager.getPrivateKey(password)
                    isLoading = false
                    
                    if (privateKey != null) {
                        // Password is correct
                        onPasswordVerified()
                    } else {
                        // Password is incorrect
                        errorMessage = "Invalid password"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid password. Please try again.")
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
                    Text("Unlock", fontSize = 16.sp)
                }
            }
            
            // Biometric unlock button (only show if available)
            if (canAuthenticateWithBiometrics && walletManager.isBiometricEnabled()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showBiometricPrompt = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = "Use Biometrics", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Biometrics", fontSize = 16.sp)
                }
            }
        }
    }
}
