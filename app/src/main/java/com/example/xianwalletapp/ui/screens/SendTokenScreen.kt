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
import androidx.compose.ui.platform.LocalLifecycleOwner // Added for state collection
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Added for state collection
import androidx.lifecycle.viewmodel.compose.viewModel // Added for ViewModel injection
import androidx.navigation.NavController
// XianCrypto import removed as it's likely unused directly here now
import com.example.xianwalletapp.network.TransactionResult // Keep if used for result type
import com.example.xianwalletapp.network.XianNetworkService // Keep if needed for factory
import com.example.xianwalletapp.wallet.WalletManager // Keep if needed for factory
import com.example.xianwalletapp.ui.viewmodels.WalletViewModel // Added
import com.example.xianwalletapp.ui.viewmodels.WalletViewModelFactory // Added (Assuming this exists)
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay // Added for debounce
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
    // networkService: XianNetworkService, // Provided by ViewModel
    contract: String,
    symbol: String,
    // Inject ViewModel using the factory
    viewModel: WalletViewModel = viewModel(
        factory = WalletViewModelFactory(WalletManager.getInstance(LocalContext.current), XianNetworkService.getInstance(LocalContext.current))
    )
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

    // Collect state from ViewModel
    val resolvedXnsAddress by viewModel.resolvedXnsAddress.collectAsStateWithLifecycle()
    val isXnsAddress by viewModel.isXnsAddress.collectAsStateWithLifecycle()
    val isResolvingXns by viewModel.isResolvingXns.collectAsStateWithLifecycle()
    
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
    
    // Get balance directly from ViewModel state
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    // Update local balance state when the map changes for the specific contract
    LaunchedEffect(balanceMap, contract) {
        currentBalance = balanceMap[contract] ?: 0f // Default to 0f if not found
    }
    // Trigger a refresh if the balance isn't loaded (optional, depends on ViewModel logic)
    LaunchedEffect(Unit) {
        if (balanceMap[contract] == null) {
             viewModel.refreshData() // Or a more specific fetch if available
        }
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

    // --- Effects ---

    // Debounce XNS check
    LaunchedEffect(recipientAddress) {
        if (recipientAddress.isNotBlank()) {
            delay(500) // Wait 500ms after last input change
            viewModel.checkAndResolveXns(recipientAddress)
        } else {
            viewModel.clearXnsResolution() // Clear if input is empty
        }
    }

    // Clear XNS state when the screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearXnsResolution()
        }
    }

    // --- UI ---
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
                onValueChange = {
                    recipientAddress = it
                    errorMessage = null
                    // Trigger debounced check via LaunchedEffect watching recipientAddress
                    // If input is cleared manually, clear XNS state immediately
                    if (it.isBlank()) {
                         viewModel.clearXnsResolution()
                    }
                },
                label = { Text("Recipient Address or XNS Name") },
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

            // XNS Resolution Status Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 4.dp) // Add some padding
                    .height(24.dp), // Fixed height to prevent layout jumps
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isResolvingXns) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resolving XNS...", style = MaterialTheme.typography.bodySmall)
                } else if (isXnsAddress && resolvedXnsAddress != null) {
                    Text(
                        "XNS Found: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF009688) // Teal color for success
                    )
                    Text(
                        "${resolvedXnsAddress?.take(10)}...${resolvedXnsAddress?.takeLast(6)}", // Use safe calls
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF009688)
                    )
                }
                // Optionally add a message if resolution failed AND the input doesn't look like a full address
                 else if (recipientAddress.length >= 3 && // Min XNS length
                          recipientAddress.length != 64 && // Exclude full addresses
                          recipientAddress.matches(Regex("^[a-zA-Z0-9]+$")) && // Check if it could be XNS
                          !isResolvingXns && // Not currently resolving
                          !isXnsAddress) { // Resolution failed or wasn't a valid XNS
                     Text(
                         "XNS not found or invalid.",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.error
                     )
                 }
            }
            // Removed extra closing parenthesis here
            
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
                    // --- Start of onClick Logic ---

                    // 1. Determine final recipient address FIRST
                    val finalRecipient = if (isXnsAddress) resolvedXnsAddress ?: recipientAddress else recipientAddress

                    // 2. Validate inputs
                    if (recipientAddress.isBlank()) { // Validate original input field
                        errorMessage = "Recipient address or XNS name cannot be empty"
                        return@Button
                    }
                    // Validate the determined final address format if it wasn't an XNS resolution
                    if (!isXnsAddress && finalRecipient.length != 64) {
                         errorMessage = "Invalid recipient address format"
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

                    // 3. Clear previous error
                    errorMessage = null

                    // 4. Proceed with password check / transaction sending
                    
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
                                // Call ViewModel's send function
                                val result = viewModel.sendTokenTransaction( // Use the public ViewModel function
                                    contract = contract,
                                    recipientAddress = finalRecipient, // Pass calculated recipient
                                    amount = amount,
                                    privateKey = cachedKey // Use cached key
                                    // stampLimit uses default in ViewModel function
                                )

                                // Handle result
                                if (result.success) {
                                    transactionHash = result.txHash ?: "" // Use safe call for hash
                                    showTransactionSuccessNotification(
                                        context = context,
                                        title = "Transaction Successful",
                                        message = if (isXnsAddress) "Sent $amount $symbol to $recipientAddress (${finalRecipient.take(6)}...)" else "Sent $amount $symbol to ${finalRecipient.take(6)}...${finalRecipient.takeLast(4)}",
                                        txHash = transactionHash
                                    )
                                    val record = LocalTransactionRecord(
                                        type = "Sent",
                                        amount = amount,
                                        symbol = symbol,
                                        recipient = finalRecipient, // Use finalRecipient
                                        txHash = transactionHash,
                                        contract = contract
                                    )
                                    transactionHistoryManager.addRecord(record)
                                    navController.popBackStack()
                                    android.util.Log.d("SendTokenScreen", "TRANSACTION SUCCESSFUL (cached key): $transactionHash")
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
                                    // 1. Get the private key using password (this also caches it if successful)
                                    val privateKey = walletManager.unlockWallet(password)
                                    if (privateKey == null) {
                                        errorMessage = "Invalid password"
                                        isLoading = false
                                        return@launch
                                    }

                                    // 2. Recalculate finalRecipient here as it's not in scope from the outer Button onClick
                                    val finalRecipientInDialog = if (isXnsAddress) resolvedXnsAddress ?: recipientAddress else recipientAddress
                                    // Optional: Add validation for finalRecipientInDialog here if needed

                                    // 3. Call ViewModel's send function
                                    val result = viewModel.sendTokenTransaction( // Use the public ViewModel function
                                        contract = contract,
                                        recipientAddress = finalRecipientInDialog, // Use recalculated recipient
                                        amount = amount,
                                        privateKey = privateKey
                                        // stampLimit uses default in ViewModel function
                                    )

                                    // 4. Handle the result
                                    if (result.success) {
                                        transactionHash = result.txHash ?: "" // Use safe call for hash
                                        showTransactionSuccessNotification(
                                            context = context,
                                            title = "Transaction Successful",
                                            message = if (isXnsAddress) "Sent $amount $symbol to $recipientAddress (${finalRecipientInDialog.take(6)}...)" else "Sent $amount $symbol to ${finalRecipientInDialog.take(6)}...${finalRecipientInDialog.takeLast(4)}",
                                            txHash = transactionHash
                                        )
                                        val record = LocalTransactionRecord(
                                            type = "Sent",
                                            amount = amount,
                                            symbol = symbol,
                                            recipient = finalRecipientInDialog, // Use recalculated recipient
                                            txHash = transactionHash,
                                            contract = contract
                                        )
                                        transactionHistoryManager.addRecord(record)
                                        navController.popBackStack()
                                        android.util.Log.d("SendTokenScreen", "TRANSACTION SUCCESSFUL: $transactionHash")
                                    } else {
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