package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.xianwalletapp.crypto.XianCrypto
import com.example.xianwalletapp.network.TransactionResult
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.launch
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import com.example.xianwalletapp.ui.theme.XianButtonType
import com.example.xianwalletapp.ui.theme.xianButtonColors

/**
 * Screen for sending tokens to another address
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendTokenScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    contract: String,
    symbol: String
) {
    val coroutineScope = rememberCoroutineScope()
    var recipientAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var transactionHash by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current // Get context for permission check

    // Launcher for QR Scanner Activity
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            recipientAddress = result.contents // Update address state with scanned content
            errorMessage = null // Clear previous errors
        } else {
            // Optional: Handle scan cancellation or failure (e.g., show snackbar)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("QR scan cancelled or failed.")
            }
        }
    }

    // Launcher for Camera Permission Request
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, launch scanner
            val options = ScanOptions().apply {
                setPrompt("Scan a Wallet Address QR code")
                setBeepEnabled(true)
                setOrientationLocked(false)
                captureActivity = com.journeyapps.barcodescanner.CaptureActivity::class.java // Explicitly set CaptureActivity
            }
            scanLauncher.launch(options)
        } else {
            // Permission denied
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is required to scan QR codes.")
            }
        }
    }

    // Function to check permission and launch scanner
    fun launchScannerWithPermissionCheck() {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, launch scanner
                val options = ScanOptions().apply {
                    setPrompt("Scan a Wallet Address QR code")
                    setBeepEnabled(true)
                    setOrientationLocked(false)
                    captureActivity = com.journeyapps.barcodescanner.CaptureActivity::class.java // Explicitly set CaptureActivity
                }
                scanLauncher.launch(options)
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Get current balance
    var currentBalance by remember { mutableStateOf(0f) }
    val publicKey = walletManager.getPublicKey() ?: ""
    
    LaunchedEffect(Unit) {
        currentBalance = networkService.getTokenBalance(contract, publicKey)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send $symbol") },
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
            // Balance display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Available Balance",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = String.format("%.1f %s", currentBalance, symbol),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            // Recipient address field
            OutlinedTextField(
                value = recipientAddress,
                onValueChange = { recipientAddress = it; errorMessage = null },
                label = { Text("Recipient Address") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true, // Added comma
                trailingIcon = {
                    IconButton(onClick = { launchScannerWithPermissionCheck() }) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Scan QR Code"
                        )
                    }
                }
            )
            
            // Amount field
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; errorMessage = null },
                label = { Text("Amount") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                trailingIcon = { Text(symbol, modifier = Modifier.padding(end = 16.dp)) }
            )
            
            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Send button
            Button(
                onClick = {
                    // Validate input
                    if (recipientAddress.isBlank()) {
                        errorMessage = "Recipient address cannot be empty"
                        return@Button
                    }
                    
                    if (amount.isBlank() || amount.toFloatOrNull() == null || amount.toFloat() <= 0) {
                        errorMessage = "Enter a valid amount"
                        return@Button
                    }
                    
                    if (amount.toFloat() > currentBalance) {
                        errorMessage = "Insufficient balance"
                        return@Button
                    }
                    
                    // Clear error and show password dialog
                    errorMessage = null
                    showPasswordDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = xianButtonColors(XianButtonType.PRIMARY),
                enabled = !isLoading
            ) {
                Text("Send Tokens")
            }
        }
        
        // Password dialog
        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                title = { Text("Enter Password") },
                text = {
                    Column {
                        Text("Please enter your wallet password to confirm the transaction.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Process transaction
                            showPasswordDialog = false
                            isLoading = true
                            
                            coroutineScope.launch {
                                try {
                                    // Get the private key using password
                                    val privateKey = walletManager.getPrivateKey(password)
                                    if (privateKey == null) {
                                        errorMessage = "Invalid password"
                                        isLoading = false
                                        return@launch
                                    }
                                    
                                    // ENFOQUE SIMPLIFICADO: Crear directamente la transacción para enviar tokens
                                    // sin pasar por todo el proceso de la versión web
                                    
                                    // 1. Crear un objeto de transacción simplificado que contiene solo lo necesario
                                    val simpleTransaction = JSONObject().apply {
                                        put("contract", contract)
                                        put("function", "transfer")
                                        put("kwargs", JSONObject().apply {
                                            put("to", recipientAddress)
                                            put("amount", java.math.BigDecimal(amount))
                                        })
                                    }
                                    
                                    android.util.Log.d("SendTokenScreen", "Creando transacción simplificada: $simpleTransaction")
                                    
                                    // 2. Enviar la transacción usando un método simplificado que maneja todo internamente
                                    val result = networkService.sendTransaction(
                                        contract = contract,
                                        method = "transfer",
                                        kwargs = simpleTransaction.getJSONObject("kwargs"),
                                        privateKey = privateKey,
                                        stampLimit = 500000  // Aumentado significativamente para cubrir transacciones más grandes
                                    )
                                    
                                    // 3. Manejar el resultado como antes
                                    android.util.Log.d("SendTokenScreen", "Resultado de transacción: $result")
                                    
                                    if (result.success) {
                                        // Mostrar éxito
                                        transactionHash = result.txHash
                                        showSuccessDialog = true
                                        android.util.Log.d("SendTokenScreen", "TRANSACTION SUCCESSFUL: ${result.txHash}")
                                    } else {
                                        // Mostrar error
                                        errorMessage = result.errors ?: "Transaction failed with unknown error"
                                        android.util.Log.e("SendTokenScreen", "Transaction failed: ${result.errors}")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error during transaction: ${e.message}"
                                    android.util.Log.e("SendTokenScreen", "Exception during transaction", e)
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        colors = xianButtonColors(XianButtonType.SECONDARY),
                        enabled = password.isNotEmpty()
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Success dialog
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                title = { Text("Transaction Successful") },
                text = { 
                    Column {
                        Text("Your transaction has been successfully submitted to the network.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("The tokens will be transferred to the recipient shortly.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Transaction ID: $transactionHash", fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            navController.popBackStack()
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
    }
}