package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random

import android.util.Log

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.outlined.ContactPage
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
import net.xian.xianwalletapp.network.TransactionResult // Keep if used for result type
import net.xian.xianwalletapp.network.XianNetworkService // Keep if needed for factory
import net.xian.xianwalletapp.wallet.WalletManager // Keep if needed for factory
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel // Added
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModelFactory // Added (Assuming this exists)
import net.xian.xianwalletapp.ui.viewmodels.FeeEstimationState // Added for fee state
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay // Added for debounce
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant

import net.xian.xianwalletapp.data.LocalTransactionRecord // Added
import net.xian.xianwalletapp.data.TransactionHistoryManager // Added
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import net.xian.xianwalletapp.ui.components.QRScannerView // Added for integrated QR scanner
import net.xian.xianwalletapp.navigation.XianDestinations // Added for navigation
import net.xian.xianwalletapp.network.TokenBalanceResponse // Added for balance API
import net.xian.xianwalletapp.ui.components.PasswordTextField

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
        factory = WalletViewModelFactory(
            context = LocalContext.current, // Pass context
            walletManager = WalletManager.getInstance(LocalContext.current), // Pass WalletManager instance
            networkService = XianNetworkService.getInstance(LocalContext.current) // Pass XianNetworkService instance
        )
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
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var confirmationDetails by remember { mutableStateOf<Triple<String, String, String>?>(null) } // recipient, amount, fee
    var showTransactionProcessing by remember { mutableStateOf(false) }
    var showTransactionSuccess by remember { mutableStateOf(false) }
    var transactionSuccessDetails by remember { mutableStateOf<Triple<String, String, String>?>(null) } // amount, symbol, recipient    // Collect state from ViewModel
    val resolvedXnsAddress by viewModel.resolvedXnsAddress.collectAsStateWithLifecycle()
    val isXnsAddress by viewModel.isXnsAddress.collectAsStateWithLifecycle()
    val isResolvingXns by viewModel.isResolvingXns.collectAsStateWithLifecycle()
    val estimatedFeeState by viewModel.estimatedFeeState.collectAsStateWithLifecycle() // Observe fee state
    var isEstimatingFee by remember { mutableStateOf(false) } // Local loading state for button
    var isPasswordDialogForTx by remember { mutableStateOf(false) } // Track if password dialog is for this specific TX flow
    var showQRScanner by remember { mutableStateOf(false) } // State to show integrated QR scanner
    var saveAddressAfterTransaction by remember { mutableStateOf(false) } // State for saving address checkbox
    var isLoadingBalance by remember { mutableStateOf(false) } // State for loading balance

    // context variable moved up

    // Legacy QR Scanner Activity (kept as fallback)
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

    // Listen for selected address from AddressBook screen
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.get<String>("selected_address")?.let { selectedAddress ->
            recipientAddress = selectedAddress
            errorMessage = null
            // Clear the saved state to prevent re-triggering
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("selected_address")
        }
    }

    // Launcher for Camera Permission Request
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, show integrated scanner
            showQRScanner = true
        } else {
            // Permission denied
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is required to scan QR codes.")
            }
        }
    }    // Function to check permission and launch integrated scanner
    fun launchScannerWithPermissionCheck() {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, show integrated scanner
                showQRScanner = true
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

    // --- Effect to handle Fee Estimation Result ---
    LaunchedEffect(estimatedFeeState) {
        when (val state = estimatedFeeState) {
            is FeeEstimationState.Success -> {
                confirmationDetails?.let { details ->
                    confirmationDetails = Triple(details.first, details.second, state.fee) // Update fee in details
                    showConfirmationDialog = true
                }
                isEstimatingFee = false
                viewModel.clearFeeEstimationState() // Reset state after handling
            }
            // Removed FeeEstimationState.RequiresUnlock handling, as UI checks lock state first
            is FeeEstimationState.Failure -> {
                confirmationDetails?.let { details ->
                    confirmationDetails = Triple(details.first, details.second, "Estimation Failed") // Show error
                    showConfirmationDialog = true
                }
                isEstimatingFee = false
                viewModel.clearFeeEstimationState()
            }
            is FeeEstimationState.Loading -> {
                isEstimatingFee = true // Update local loading state
            }
            is FeeEstimationState.Idle -> {
                isEstimatingFee = false // Ensure loading is false when idle
            }
        }
    }

    // --- UI ---
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
                            text = "Send $symbol",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // AddressBook icon moved to recipient address input field
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
                    .padding(bottom = 24.dp),                // Removed background color
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(colors = listOf(XianPrimary, XianPrimaryVariant)) // Use new teal colors
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                navController.navigate(XianDestinations.ADDRESS_BOOK)
                            }
                        ) {
                            Icon(
                                Icons.Outlined.ContactPage,
                                contentDescription = "Address Book",
                                tint = XianPrimary
                            )
                        }
                        IconButton(onClick = { launchScannerWithPermissionCheck() }) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Scan QR Code"
                            )
                        }
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
            
            // Amount field with Max button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; errorMessage = null },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = symbol,
                                modifier = Modifier.padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            isLoadingBalance = true
                                            val networkService = XianNetworkService.getInstance(context)
                                            val response = networkService.apiService.getTokenBalance(
                                                contractName = contract,
                                                address = publicKey
                                            )
                                            if (response.isSuccessful) {
                                                response.body()?.let { balanceResponse ->
                                                    // Apply deduction based on token type
                                                    val deduction = if (contract.equals("currency", ignoreCase = true) || contract.equals("xian", ignoreCase = true)) {
                                                        0.2 // XIAN token - deduct 0.2 for transaction fees
                                                    } else {
                                                        0.001 // Other tokens - deduct 0.001 for transaction fees
                                                    }
                                                    
                                                    val maxAmount = (balanceResponse.balance - deduction).coerceAtLeast(0.0)
                                                    amount = maxAmount.toString()
                                                }
                                            } else {
                                                Log.e("SendTokenScreen", "Failed to fetch balance: ${response.message()}")
                                                snackbarHostState.showSnackbar("Failed to fetch balance")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SendTokenScreen", "Error fetching balance", e)
                                            snackbarHostState.showSnackbar("Error fetching balance")
                                        } finally {
                                            isLoadingBalance = false
                                        }
                                    }
                                },
                                enabled = !isLoadingBalance,
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (isLoadingBalance) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = XianPrimary,
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Text(
                                        "Max",
                                        fontSize = 11.sp,
                                        color = XianPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
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

                    // 4. Check if the private key is ACTUALLY available in memory
                    val isKeyAvailable = walletManager.getUnlockedPrivateKey() != null
                    // Store details needed for potential password unlock AND fee estimation/confirmation
                    confirmationDetails = Triple(finalRecipient, amount, "") // Fee is empty initially

                    if (!isKeyAvailable) {
                        // Key is NOT available (regardless of setting), need password.
                        android.util.Log.d("SendTokenScreen", "Wallet key not cached, showing password dialog.")
                        isPasswordDialogForTx = true
                        password = ""
                        showPasswordDialog = true
                    } else {
                        // Key IS available, proceed directly to fee estimation.
                        // Confirmation dialog will be shown by LaunchedEffect.
                        android.util.Log.d("SendTokenScreen", "Wallet key available, requesting fee estimation.")
                        confirmationDetails = Triple(finalRecipient, amount, "Estimating...") // Update status
                        viewModel.requestFeeEstimation(
                            contract = contract,
                            recipientAddress = finalRecipient,
                            amount = amount
                        )
                    }
                    // --- Logic below moved to Confirmation Dialog's Confirm button ---
                    /*
                    
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
                        // showPasswordDialog = true // Moved to confirmation dialog
                    }
                    */
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = xianButtonColors(XianButtonType.PRIMARY),
                enabled = !isLoading && !isResolvingXns && !isEstimatingFee // Disable while loading, resolving, or estimating
            ) {
                Text("Review Transaction") // Changed button text
            }
        }
        // --- Confirmation Dialog ---
        if (showConfirmationDialog && confirmationDetails != null) {
            AlertDialog(
                onDismissRequest = {
                    showConfirmationDialog = false
                    viewModel.clearFeeEstimationState() // Also clear state on dismiss
                },
                title = { Text("Confirm Transaction") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Please review the details before confirming:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Recipient: ${confirmationDetails!!.first.let { if (it.length > 20) "${it.take(10)}...${it.takeLast(6)}" else it }}") // Show shortened address if long
                        Text("Amount: ${confirmationDetails!!.second} $symbol")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Estimated Fee: ${confirmationDetails!!.third}")
                            if (isEstimatingFee) { // Show spinner if fee is still loading within dialog (edge case)
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Save address option - moved from above Review Transaction button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveAddressAfterTransaction = !saveAddressAfterTransaction },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveAddressAfterTransaction,
                                onCheckedChange = { saveAddressAfterTransaction = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = XianPrimary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save this wallet address after transaction",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.clickable { saveAddressAfterTransaction = !saveAddressAfterTransaction }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmationDialog = false // Dismiss confirmation dialog
                            showTransactionProcessing = true // Show processing modal
                            viewModel.clearFeeEstimationState() // Clear state on confirm too
                            val (recipientToSend, amountToSend, _) = confirmationDetails!! // Destructure details

                            // Store details for success modal
                            transactionSuccessDetails = Triple(amountToSend, symbol, recipientToSend)

                            // --- Start: Logic moved from original button's onClick ---
                            // Wallet is guaranteed to be unlocked here (either was already, or password was just entered)
                            val unlockedPrivateKey = walletManager.getUnlockedPrivateKey()

                            if (unlockedPrivateKey == null) {
                                // This shouldn't happen in the new flow, but handle defensively
                                showTransactionProcessing = false
                                errorMessage = "Wallet key unavailable. Please try again."
                                Log.e("SendTokenScreen", "Confirm & Send clicked but private key is null unexpectedly.")
                                return@Button
                            }

                            // Proceed to send the transaction
                            coroutineScope.launch {
                                try {
                                    val result = viewModel.sendTokenTransaction(
                                        contract = contract,
                                        recipientAddress = recipientToSend,
                                        amount = amountToSend,
                                        privateKey = unlockedPrivateKey // Use the available key
                                    )
                                    // Handle result
                                    if (result.success) {
                                        transactionHash = result.txHash ?: ""
                                        showTransactionSuccessNotification(
                                            context = context,
                                            title = "Transaction Successful",
                                            message = "Sent $amountToSend $symbol to ${recipientToSend.take(6)}...${recipientToSend.takeLast(4)}",
                                            txHash = transactionHash
                                        )
                                        val record = LocalTransactionRecord(
                                            type = "Sent",
                                            amount = amountToSend,
                                            symbol = symbol,
                                            recipient = recipientToSend,
                                            txHash = transactionHash,
                                            contract = contract
                                        )
                                        transactionHistoryManager.addRecord(record)
                                        
                                        // Show success modal
                                        showTransactionProcessing = false
                                        showTransactionSuccess = true
                                        
                                        Log.d("SendTokenScreen", "TRANSACTION SUCCESSFUL: $transactionHash")
                                    } else {
                                        showTransactionProcessing = false
                                        errorMessage = result.errors ?: "Transaction failed with unknown error"
                                        Log.e("SendTokenScreen", "Transaction failed: ${result.errors}")
                                    }
                                } catch (e: Exception) {
                                    showTransactionProcessing = false
                                    errorMessage = "Error during transaction: ${e.message}"
                                    Log.e("SendTokenScreen", "Exception during transaction", e)
                                }
                            }
                            // --- End: Logic moved from original button's onClick ---
                        },
                        colors = xianButtonColors(XianButtonType.PRIMARY),
                        // Disable confirm if fee estimation failed
                        enabled = !isLoading && confirmationDetails?.third != "Estimation Failed"
                    ) {
                        Text("Confirm & Send")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showConfirmationDialog = false
                        viewModel.clearFeeEstimationState() // Clear state on cancel
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- Transaction Processing Modal ---
        if (showTransactionProcessing) {
            AlertDialog(
                onDismissRequest = { /* Prevent dismissal during processing */ },
                title = null,
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Hourglass loading indicator with rotation animation
                        var rotation by remember { mutableStateOf(0f) }
                        
                        LaunchedEffect(Unit) {
                            while (showTransactionProcessing) {
                                rotation += 360f
                                delay(1500) // Rotate every 1.5 seconds
                            }
                        }
                        
                        val animatedRotation by animateFloatAsState(
                            targetValue = rotation,
                            animationSpec = tween(
                                durationMillis = 1500,
                                easing = LinearEasing
                            ),
                            label = "HourglassRotation"
                        )
                        
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Processing",
                            tint = XianPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .rotate(animatedRotation)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Processing Transaction...",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = { /* No buttons during processing */ },
                dismissButton = { /* No buttons during processing */ }
            )
        }

        // --- Transaction Success Modal ---
        if (showTransactionSuccess && transactionSuccessDetails != null) {
            // Trigger haptic feedback when success modal appears
            LaunchedEffect(showTransactionSuccess) {
                if (showTransactionSuccess) {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(200)
                    }
                }
            }
            
            AlertDialog(
                onDismissRequest = { /* Prevent dismissal, user must click OK */ },
                title = null,
                text = {
                    Box(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.wrapContentSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Success checkmark circle
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        color = Color.Cyan,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Color.Black, // Changed from White to Black
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            val (amount, tokenSymbol, recipient) = transactionSuccessDetails!!
                            val shortAddress = "${recipient.take(6)}...${recipient.takeLast(6)}"
                            
                            Text(
                                text = "You sent $amount $tokenSymbol tokens to $shortAddress",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Confetti effect - positioned absolutely within the dialog bounds
                        ConfettiEffect(
                            modifier = Modifier.matchParentSize()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val recipientAddress = transactionSuccessDetails?.third
                            showTransactionSuccess = false
                            transactionSuccessDetails = null
                            
                            // Navigate to AddressBook with pre-filled address only if user chose to save
                            if (saveAddressAfterTransaction && recipientAddress != null) {
                                navController.navigate("${XianDestinations.ADDRESS_BOOK}?prefilledAddress=${recipientAddress}")
                            } else {
                                navController.popBackStack()
                            }
                        },
                        colors = xianButtonColors(XianButtonType.PRIMARY)
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = { /* No cancel button, user must click OK */ }
            )
        }

        // --- Password Dialog ---
        // Password dialog
        if (showPasswordDialog) {
            var dialogErrorMessage by remember { mutableStateOf("") }
            
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                title = { Text("Enter Password") },
                text = {
                    Column {
                        Text("Please enter your wallet password to confirm the transaction.")
                        Spacer(modifier = Modifier.height(16.dp))
                        PasswordTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                dialogErrorMessage = "" // Clear error when user types
                            },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Show error message within the dialog
                        if (dialogErrorMessage.isNotEmpty()) {
                            Text(
                                text = dialogErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // This onClick is for the Password Dialog's confirm button
                            if (!isPasswordDialogForTx) {
                                // Should not happen if dialog is only shown for TX flow
                                showPasswordDialog = false
                                return@Button
                            }

                            isLoading = true // Show loading indicator while unlocking
                            dialogErrorMessage = "" // Clear dialog errors
                            coroutineScope.launch {
                                var unlockedKey: ByteArray? = null
                                try {
                                    // 1. Attempt to unlock wallet
                                    unlockedKey = walletManager.unlockWallet(password)
                                    if (unlockedKey == null) {
                                        // Show error within dialog instead of main screen
                                        dialogErrorMessage = "Invalid password"
                                        // Keep password dialog open, stop loading
                                        isLoading = false
                                        return@launch // Exit coroutine on invalid password
                                    }

                                    // 2. Wallet unlocked successfully! Dismiss password dialog.
                                    showPasswordDialog = false
                                    isPasswordDialogForTx = false // Reset flag

                                    // 3. Get recipient/amount from stored details
                                    val (recipient, amountVal, _) = confirmationDetails ?: run {
                                        errorMessage = "Confirmation details missing after unlock. Please try again."
                                        isLoading = false // Stop loading
                                        return@launch // Exit coroutine if details are missing
                                    }

                                    // 4. Now trigger fee estimation (wallet is unlocked)
                                    Log.d("SendTokenScreen", "Password correct, wallet unlocked. Requesting fee estimation.")
                                    confirmationDetails = Triple(recipient, amountVal, "Estimating...") // Update status
                                    viewModel.requestFeeEstimation(
                                        contract = contract,
                                        recipientAddress = recipient,
                                        amount = amountVal
                                    )
                                    // LaunchedEffect will handle showing the *Confirmation* Dialog

                                } catch (e: Exception) {
                                    // Show error within dialog for unlock errors
                                    dialogErrorMessage = "Error during unlock: ${e.message}"
                                    Log.e("SendTokenScreen", "Exception after password entry", e)
                                    isLoading = false
                                } finally {
                                    // Stop loading indicator *after* unlock attempt & fee request
                                    isLoading = false
                                }
                            }
                        },
                        colors = xianButtonColors(XianButtonType.SECONDARY),
                        enabled = password.isNotEmpty() && !isLoading // Disable while loading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Confirm")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasswordDialog = false
                            isPasswordDialogForTx = false // Reset flag on cancel
                        },
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Integrated QR Scanner
        if (showQRScanner) {
            QRScannerView(
                onQRCodeScanned = { qrCode ->
                    recipientAddress = qrCode
                    errorMessage = null
                    showQRScanner = false
                    // If input is cleared manually, clear XNS state immediately
                    if (qrCode.isBlank()) {
                        viewModel.clearXnsResolution()
                    }
                },
                onDismiss = {
                    showQRScanner = false
                },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        // Success dialog removed - replaced by notification
    }
}

@Composable
private fun ConfettiEffect(
    modifier: Modifier = Modifier
) {
    var showConfetti by remember { mutableStateOf(true) }
    var confettiAlpha by remember { mutableStateOf(1f) }
    
    // Timer to stop confetti after 2 seconds
    LaunchedEffect(Unit) {
        delay(2000) // Wait 2 seconds
        showConfetti = false
        // Fade out animation
        val fadeOutDuration = 500L
        val steps = 20
        val stepDelay = fadeOutDuration / steps
        val alphaStep = 1f / steps
        
        repeat(steps) {
            confettiAlpha -= alphaStep
            delay(stepDelay)
        }
        confettiAlpha = 0f
    }
    
    if (showConfetti || confettiAlpha > 0f) {
        val confettiPieces = remember { 
            (1..20).map { // Reduced from 30 to 20 pieces for better performance in smaller space
                ConfettiPiece(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    color = listOf(
                        Color.Red, Color.Blue, Color.Green, Color.Yellow, 
                        Color.Magenta, Color.Cyan, Color(0xFFFF9800)
                    ).random(),
                    size = Random.nextFloat() * 6f + 3f // Slightly smaller pieces
                )
            }
        }
        
        val infiniteTransition = rememberInfiniteTransition(label = "confetti")
        
        Box(modifier = modifier.alpha(confettiAlpha)) {
            confettiPieces.forEach { piece ->
                val animatedY by infiniteTransition.animateFloat(
                    initialValue = -0.1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = (2000 + Random.nextInt(1000)),
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "confetti_y_${piece.hashCode()}"
                )
                
                val animatedRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = (1000 + Random.nextInt(500)),
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "confetti_rotation_${piece.hashCode()}"
                )
                
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = piece.x * size.width
                            translationY = animatedY * size.height
                            rotationZ = animatedRotation
                            alpha = if (animatedY > 0.8f) (1f - (animatedY - 0.8f) / 0.2f) else 1f
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(piece.size.dp)
                            .background(
                                color = piece.color,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float
)

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
        .setSmallIcon(net.xian.xianwalletapp.R.mipmap.ic_launcher) // Use app launcher icon
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
