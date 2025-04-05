package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
// Removed duplicate import
import androidx.activity.result.contract.ActivityResultContracts
// Removed duplicate import
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import com.example.xianwalletapp.ui.theme.XianBlue

import com.example.xianwalletapp.data.LocalTransactionRecord // Added
import com.example.xianwalletapp.data.TransactionHistoryManager // Added
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
    // var showSuccessDialog by remember { mutableStateOf(false) } // Removed: Replaced with notification
    var transactionHash by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current // Get context for permission check and history manager
    val transactionHistoryManager = remember { TransactionHistoryManager(context) } // Pass context variable
    
    // context variable moved up

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

    // --- Notification Permission Handling (Android 13+) ---
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                // Optional: Show a snackbar or message explaining why the permission is needed
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Notification permission denied. You won't receive success notifications.")
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


// Code block cleaned up
    
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
                // Removed background color
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(colors = listOf(Color.Yellow, XianBlue)) // Use XianBlue
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
                    
                    // Clear error
                    errorMessage = null
                    
                    // Check if password is required for this action
                    // Check if password is required for this action based on startup setting & cache
                    val requirePasswordOnStartup = walletManager.getRequirePassword()
                    val cachedKey = walletManager.getUnlockedPrivateKey() // Check if key is already cached

                    if (requirePasswordOnStartup && cachedKey != null) {
                        // If password WAS required on startup AND key is cached, proceed directly
                        android.util.Log.d("SendTokenScreen", "Using cached key for transaction.")
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                // --- Transaction Logic (Using cachedKey) ---
                                val simpleTransaction = JSONObject().apply {
                                    put("contract", contract)
                                    put("function", "transfer")
                                    put("kwargs", JSONObject().apply {
                                        put("to", recipientAddress)
                                        put("amount", java.math.BigDecimal(amount))
                                    })
                                }
                                android.util.Log.d("SendTokenScreen", "Creando transacción simplificada (cached key): $simpleTransaction")
                                val result = networkService.sendTransaction(
                                    contract = contract,
                                    method = "transfer",
                                    kwargs = simpleTransaction.getJSONObject("kwargs"),
                                    privateKey = cachedKey, // Use the cached key
                                    stampLimit = 500000
                                )
                                android.util.Log.d("SendTokenScreen", "Resultado de transacción (cached key): $result")
                                if (result.success) {
                                    transactionHash = result.txHash
                                    showTransactionSuccessNotification(
                                        context = context,
                                        title = "Transaction Successful",
                                        message = "Sent $amount $symbol to ${recipientAddress.take(6)}...${recipientAddress.takeLast(4)}",
                                        txHash = transactionHash
                                    )
                                    val record = LocalTransactionRecord(
                                        type = "Sent",
                                        amount = amount,
                                        symbol = symbol,
                                        recipient = recipientAddress,
                                        txHash = transactionHash,
                                        contract = contract
                                    )
                                    transactionHistoryManager.addRecord(record)
                                    navController.popBackStack()
                                    android.util.Log.d("SendTokenScreen", "TRANSACTION SUCCESSFUL (cached key): ${result.txHash}")
                                } else {
                                    errorMessage = result.errors ?: "Transaction failed with unknown error"
                                    android.util.Log.e("SendTokenScreen", "Transaction failed (cached key): ${result.errors}")
                                }
                                // --- End Transaction Logic ---
                            } catch (e: Exception) {
                                errorMessage = "Error during transaction: ${e.message}"
                                android.util.Log.e("SendTokenScreen", "Exception during transaction (cached key)", e)
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        // If password NOT required on startup, OR if key isn't cached (e.g., startup failed?),
                        // show the password dialog to get the password.
                        android.util.Log.d("SendTokenScreen", "Showing password dialog (requireStartup=$requirePasswordOnStartup, cachedKeyIsNull=${cachedKey == null}).")
                        showPasswordDialog = true
                    }
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
                                    // Get the private key using password (this also caches it if successful)
                                    val privateKey = walletManager.unlockWallet(password)
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
                                        // Show notification instead of dialog
                                        showTransactionSuccessNotification(
                                            context = context,
                                            title = "Transaction Successful",
                                            message = "Sent $amount $symbol to ${recipientAddress.take(6)}...${recipientAddress.takeLast(4)}",
                                            txHash = transactionHash
                                        )

                                        // Save transaction to local history
                                        val record = LocalTransactionRecord(
                                            type = "Sent",
                                            amount = amount,
                                            symbol = symbol,
                                            recipient = recipientAddress,
                                            txHash = transactionHash,
                                            contract = contract
                                        )
                                        transactionHistoryManager.addRecord(record)

                                        navController.popBackStack() // Navigate back after showing notification
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
        
        // Success dialog removed - replaced by notification
    }
}


// Helper function to show notification
private fun showTransactionSuccessNotification(
    context: Context,
    title: String,
    message: String,
    txHash: String
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "transaction_success_channel"

    // --- Permission Check (Android 13+) ---
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted on Android 13+, do not show notification
            android.util.Log.w("SendTokenScreen", "POST_NOTIFICATIONS permission not granted. Skipping notification.")
            return
        }
    }
    // --- End Permission Check ---

    // Create notification channel for Android Oreo and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelName = "Transaction Status"
        val channelDescription = "Notifications for successful transactions"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
        }
        notificationManager.createNotificationChannel(channel)
    }

    // Build the notification
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(com.example.xianwalletapp.R.mipmap.ic_launcher) // Use app launcher icon
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText("$message\nTransaction ID: $txHash")) // Show full message and Tx ID
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true) // Dismiss notification when tapped

    // TODO: Add PendingIntent if you want to navigate somewhere specific when the notification is tapped
    // Example:
    // val intent = Intent(context, MainActivity::class.java) // Or specific activity
    // val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    // builder.setContentIntent(pendingIntent)

    // Show the notification
    val notificationId = System.currentTimeMillis().toInt() // Use timestamp for unique ID
    notificationManager.notify(notificationId, builder.build())
}