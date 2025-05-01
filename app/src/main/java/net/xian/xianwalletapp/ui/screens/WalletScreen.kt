package net.xian.xianwalletapp.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items // Import for LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Visibility // For View icon
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Build // Import for Build icon
import androidx.compose.material.icons.filled.Person // Import Person icon
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable // Import rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import for state collection
import androidx.lifecycle.viewmodel.compose.viewModel // Import for getting ViewModel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.navigation.XianNavArgs
import net.xian.xianwalletapp.network.TokenInfo
import coil.compose.AsyncImage // For Coil image loading - TODO: Add Coil dependency
import android.content.Intent
import android.net.Uri
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.network.NftInfo
import net.xian.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.launch
import androidx.compose.material.ExperimentalMaterialApi
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import net.xian.xianwalletapp.ui.theme.XianBlue

import net.xian.xianwalletapp.data.LocalTransactionRecord // Added
import net.xian.xianwalletapp.data.TransactionHistoryManager // Added
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel // Import ViewModel
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModelFactory // Import ViewModelFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import net.xian.xianwalletapp.data.db.NftCacheEntity // Import NftCacheEntity

/**
 * Main wallet screen showing token balances and actions
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(
    navController: NavController,
    walletManager: WalletManager, // Keep for ViewModel creation
    networkService: XianNetworkService, // Keep for ViewModel creation
    // Obtain ViewModel instance
    viewModel: WalletViewModel = viewModel(
        factory = WalletViewModelFactory(LocalContext.current, walletManager, networkService) // Pass context
    )
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // --- Collect State from ViewModel ---
    val publicKey by viewModel.publicKey.collectAsStateWithLifecycle() // Changed to collect from StateFlow
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    
    // Special handling for XIAN price - only load once at startup, not during refresh
    // Store the first non-null price we receive
    var staticXianPrice by remember { mutableStateOf<Float?>(null) }
      // Effect to capture the first non-null XIAN price value
    LaunchedEffect(Unit) {
        // At component initialization, check if we need to load the price
        if (staticXianPrice == null && xianPrice != null) {
            staticXianPrice = xianPrice
            Log.d("WalletScreen", "Captured initial XIAN price: $staticXianPrice")
        }
    }
    
    // Also observe price changes, but only update our static value if it's still null
    LaunchedEffect(xianPrice) {
        if (xianPrice != null && staticXianPrice == null) {
            staticXianPrice = xianPrice
            Log.d("WalletScreen", "Captured delayed XIAN price: $staticXianPrice")
        }
    }
    
    val nftList by viewModel.nftList.collectAsStateWithLifecycle() // Now collects List<NftCacheEntity>
    val displayedNftInfo by viewModel.displayedNftInfo.collectAsStateWithLifecycle() // Now collects NftCacheEntity?
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isNodeConnected by viewModel.isNodeConnected.collectAsStateWithLifecycle()
    val isNftLoading by viewModel.isNftLoading.collectAsStateWithLifecycle()
    val ownedXnsNames by viewModel.ownedXnsNames.collectAsStateWithLifecycle() // Collect owned XNS names
    val xnsNameExpirations by viewModel.xnsNameExpirations.collectAsStateWithLifecycle() // Collect expirations

    // --- Local UI State (Dialogs, Snackbar, etc.) ---
    var showAddTokenDialog by remember { mutableStateOf(false) }
    var newTokenContract by remember { mutableStateOf("") }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State for NFTs
    var showNftDropdown by remember { mutableStateOf(false) } // Control dropdown visibility

    // State for Local Activity
    var transactionHistory by remember { mutableStateOf<List<LocalTransactionRecord>>(emptyList()) }
    var isHistoryLoading by remember { mutableStateOf(false) }
    
    
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // NFT Image Preview Box (Clickable) & Dropdown
                        Box {
                            // Use displayedNftInfo (NftCacheEntity?)
                            if (displayedNftInfo != null) {
                                AsyncImage(
                                    model = displayedNftInfo?.imageUrl, // Use imageUrl from NftCacheEntity
                                    contentDescription = "Selected NFT Preview",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { showNftDropdown = true } // Make image clickable
                                        .padding(end = 8.dp),
                                    placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                                    error = painterResource(id = R.drawable.ic_launcher_background)
                                )
                            } else if (nftList.isNotEmpty()) {
                                // Placeholder if no NFT selected but list is not empty
                                Box(modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.Gray, RoundedCornerShape(4.dp))
                                    .clickable { showNftDropdown = true }
                                    .padding(end = 8.dp)
                                )
                            }

                            // Dropdown Menu for NFT Selection
                            DropdownMenu(
                                expanded = showNftDropdown,
                                onDismissRequest = { showNftDropdown = false }
                            ) {
                                // Iterate over nftList (List<NftCacheEntity>)
                                nftList.forEach { nft ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AsyncImage(
                                                    model = nft.imageUrl, // Use imageUrl from NftCacheEntity
                                                    contentDescription = nft.name,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .padding(end = 8.dp),
                                                    placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                                                    error = painterResource(id = R.drawable.ic_launcher_background)
                                                )
                                                Text(text = nft.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setPreferredNft(nft) // Pass NftCacheEntity
                                            showNftDropdown = false // Close dropdown
                                            android.util.Log.d("WalletScreen", "Selected NFT: ${nft.contractAddress}")
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Original Title Text
                        Text(
                            text = "XIAN",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " WALLET",
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Connection status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // Status text
                        Text(
                            text = if (isNodeConnected) "Connected" else "Disconnected",
                            fontSize = 12.sp,
                            color = if (isNodeConnected) 
                                Color(0xFF4CAF50) // Green
                            else 
                                Color(0xFFF44336) // Red
                        )
                        
                        // Status indicator dot
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(8.dp)
                                .background(
                                    color = if (isNodeConnected) 
                                        Color(0xFF4CAF50) // Green
                                    else 
                                        Color(0xFFF44336), // Red
                                    shape = CircleShape
                                )
                        )
                    }
                    
                    IconButton(onClick = { navController.navigate(XianDestinations.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTokenDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Token")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(XianDestinations.WEB_BROWSER) },
                    icon = { Icon(Icons.Default.Language, contentDescription = "Web Browser") },
                    label = { Text("Web Browser") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(XianDestinations.ADVANCED) },
                    icon = { Icon(Icons.Filled.Build, contentDescription = "Advanced") }, // Changed icon to Filled.Build (wrench)
                    label = { Text("Advanced") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(XianDestinations.NEWS) },
                    icon = { Icon(Icons.Default.Newspaper, contentDescription = "News") },
                    label = { Text("News") }
                )
            }
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isLoading), // Use combined isLoading
            onRefresh = { viewModel.refreshData() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // XIAN Balance Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    border = BorderStroke(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(colors = listOf(Color.Yellow, XianBlue)) // Use XianBlue
                    )
                    // Removed background color
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
                    ) {
                        // Label
                        Text(
                            text = "XIAN Price", // Changed Label again as requested
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp)) // Add space between label and balance                        // Display balance or loading indicator
                        if (staticXianPrice == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            // Display the static price that doesn't update with refresh
                            val priceText = staticXianPrice?.let { "%.6f".format(it) } ?: "---" // Format to 6 decimals or show placeholder
                            Text(
                                text = priceText,
                                fontSize = 40.sp, // Set specific larger font size
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                        // Removed Spacer and Row from inside the card
                    }
                } // End of Card

                Spacer(modifier = Modifier.height(16.dp)) // Add space after the card

                // Row moved outside the Card and text changed to English
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround, // Distribute items evenly
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Buy XIAN Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val urlToLoad = "https://dex-trade.com/spot/trading/XIANUSDT?interface=classic"
                            val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                            navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Buy XIAN", // Changed to English
                            tint = MaterialTheme.colorScheme.onSurface // Adjusted tint for outside card
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Buy XIAN", // Changed to English
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface // Adjusted color for outside card
                        )
                    }

                    // Swap Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val urlToLoad = "https://snakexchange.org/"
                            val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                            // Assuming WebBrowser destination takes url as a query parameter
                            navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap", // Changed to English
                            tint = MaterialTheme.colorScheme.onSurface // Adjusted tint for outside card
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Swap", // Changed to English
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface // Adjusted color for outside card
                        )
                    }

                    // Staking Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val urlToLoad = "https://snakexchange.org/farms/"
                            val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                            navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance, // Using AccountBalance for Staking
                            contentDescription = "Staking",
                            tint = MaterialTheme.colorScheme.onSurface // Adjusted tint for outside card
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Staking",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface // Adjusted color for outside card
                        )
                    }
                }
                
                // Spacer to replace removed debug buttons
                Spacer(modifier = Modifier.height(16.dp))

                // Tabs for Tokens/NFTs
                var selectedTabIndex by remember { mutableStateOf(0) }
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Tokens") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Collectibles") } // Changed from "NFTs"
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("Local Activity") }
                    )
                }

                // Load Local Activity data when tab is selected (NFTs are loaded earlier now)
                LaunchedEffect(selectedTabIndex) {
                     if (selectedTabIndex == 2) {
                        // Load transaction history when tab is selected
                        isHistoryLoading = true
                        val historyManager = TransactionHistoryManager(context)
                        transactionHistory = historyManager.loadRecords()
                        android.util.Log.d("WalletScreen", "Loaded ${transactionHistory.size} history records.")
                        isHistoryLoading = false
                    }
                }

                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content based on selected tab
                when (selectedTabIndex) {
                    0 -> {
                        // Tokens tab
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (tokens.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No tokens added yet.\nClick the + button to add a token.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn {
                                items(tokens) { contract ->
                                    val tokenInfo = tokenInfoMap[contract]
                                    val balance = balanceMap[contract] ?: 0f
                                    
                                    // This block was already applied correctly in the previous step,
                                    // but included here for context. No changes needed in this SEARCH/REPLACE.
                                    TokenItem(
                                        contract = contract, // Pass contract name
                                        name = tokenInfo?.name ?: contract,
                                        symbol = tokenInfo?.symbol ?: "",
                                        logoUrl = tokenInfo?.logoUrl, // Pass logo URL
                                        balance = balance,
                                        xianPrice = if (contract == "currency") xianPrice else null, // Pass price only for XIAN
                                        onSendClick = {
                                            navController.navigate(
                                                "${XianDestinations.SEND_TOKEN}?${XianNavArgs.TOKEN_CONTRACT}=$contract&${XianNavArgs.TOKEN_SYMBOL}=${tokenInfo?.symbol ?: ""}"
                                            )
                                        },
                                        onReceiveClick = {
                                            navController.navigate(XianDestinations.RECEIVE_TOKEN)
                                        },
                                        onRemoveClick = {
                                            if (contract != "currency") {
                                                viewModel.removeToken(contract) // Call ViewModel function
                                                coroutineScope.launch {
                                                    // Optional: Show snackbar, or let ViewModel handle feedback
                                                    snackbarHostState.showSnackbar("Token removal initiated")
                                                }
                                            }
                                        }
                                    )
                                    
                                }
                            }
                        }
                    }
                    1 -> {
                        // Collectibles tab (NFTs and XNS Names)

                        // *** ADD LOGGING HERE ***
                        Log.d("WalletScreen", "Collectibles Tab: nftList size = ${nftList.size}, ownedXnsNames size = ${ownedXnsNames.size}")

                        // Use isNftLoading for this specific section's loading state
                        if (isNftLoading && nftList.isEmpty() && ownedXnsNames.isEmpty()) { // Check both lists for initial loading
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (nftList.isEmpty() && ownedXnsNames.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No Collectibles found.", // Updated text
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Combine NFTs and XNS names for the grid
                            val totalItems = nftList.size + ownedXnsNames.size

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Render NFTs first
                                items(nftList) { nft ->
                                    NftItem(
                                        nftInfo = nft,
                                        onViewClick = { url ->
                                            url?.let {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Could not open URL: ${e.message}")
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                // Render XNS Names after NFTs
                                items(ownedXnsNames) { xnsName ->
                                     // *** ADD LOGGING HERE (Optional) ***
                                     // Log.d("WalletScreen", "Rendering XnsNameItem for: $xnsName")
                                    val expiration = xnsNameExpirations[xnsName]
                                    // Calculate remaining days from expiration Instant
                                    val remainingDays = expiration?.let {
                                        val now = java.time.Instant.now()
                                        // Use Instant.ofEpochSecond to convert Long to Instant
                                        val expirationInstant = java.time.Instant.ofEpochSecond(it)
                                        val duration = java.time.Duration.between(now, expirationInstant)
                                        duration.toDays().coerceAtLeast(0) // Ensure non-negative days
                                    }
                                    XnsNameItem(
                                        navController = navController, // Pass NavController
                                        username = xnsName, // Corrected parameter name
                                        remainingDays = remainingDays // Corrected parameter name and pass calculated value
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Local Activity tab
                        if (isHistoryLoading) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (transactionHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No local transaction history found.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp) // Add padding for FAB
                            ) {
                                items(transactionHistory) { record ->
                                    TransactionRecordItem(record = record)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add token dialog
    if (showAddTokenDialog) {
        AlertDialog(
            onDismissRequest = { showAddTokenDialog = false },
            title = { Text("Add Token") },
            text = {
                Column {
                    Text("Enter the contract name of the token you want to add:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTokenContract,
                        onValueChange = { newTokenContract = it },
                        label = { Text("Contract Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTokenContract.isNotBlank()) {
                            // Call ViewModel to handle token addition and refresh
                            viewModel.addTokenAndRefresh(newTokenContract) // TODO: Implement in ViewModel
                            newTokenContract = "" // Clear input field
                            showAddTokenDialog = false // Close dialog
                            coroutineScope.launch {
                                // Optional: Show snackbar, or let ViewModel handle feedback
                                snackbarHostState.showSnackbar("Token added (pending refresh)")
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TokenItem(
    contract: String, // Added contract parameter
    name: String,
    symbol: String,
    logoUrl: String?, // Added logo URL parameter
    balance: Float,
    xianPrice: Float? = null, // Added optional price parameter
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Token icon using AsyncImage
                AsyncImage(
                    model = logoUrl,
                    contentDescription = "$name Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), // Placeholder background
                    contentScale = androidx.compose.ui.layout.ContentScale.Inside, // Changed from Fit to Inside
                    // Fallback logic: Show XIAN logo for currency, or a generic icon for others
                    error = if (contract == "currency") {
                        painterResource(id = R.drawable.xian_logo) // Use the renamed xian_logo.jpg
                    } else {
                        painterResource(id = android.R.drawable.ic_menu_gallery) // Generic fallback for other tokens
                    },
                    placeholder = painterResource(id = R.drawable.xian_logo) // Use the renamed xian_logo.jpg
                )
                
                // Token details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        // Display "XIANCurrency" specifically for the native token
                        text = if (contract == "currency") "XIAN Currency" else name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = symbol,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                
                // Token balance and USD value (if applicable)
                Column(horizontalAlignment = Alignment.End) { // Wrap in Column, align text to the end
                    Text(
                        text = "%.1f".format(balance), // Format balance to 1 decimal
                        style = MaterialTheme.typography.bodyLarge, // Use consistent styling
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Show USD value below balance only for XIAN currency
                    if (contract == "currency" && xianPrice != null) {
                        val usdValue = balance * xianPrice
                        Text(
                            text = "$%.2f".format(usdValue), // Format as $0.00
                            style = MaterialTheme.typography.bodyMedium, // Correct: fontSize is part of the style
                            color = Color.Gray, // Use a less prominent color
                            modifier = Modifier.padding(top = 2.dp) // Add a little space
                        ) // Corrected: Removed invalid fontSize parameter
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (contract != "currency") {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove Token",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
                
                Row {
                    Button(
                        onClick = onSendClick,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = xianButtonColors(XianButtonType.PRIMARY)
                    ) {
                        Text("Send")
                    }
                    
                    Button(
                        onClick = onReceiveClick,
                        colors = xianButtonColors(XianButtonType.SECONDARY)
                    ) {
                        Text("Receive")
                    }
                }
            }
        }
    }
}


// --- NftItem Composable --- Updated to use NftCacheEntity
@Composable
fun NftItem(
    nftInfo: NftCacheEntity, // Changed type to NftCacheEntity
    onViewClick: (String?) -> Unit // Accept nullable String for URL
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // NFT Image
            AsyncImage(
                model = nftInfo.imageUrl, // Use imageUrl from NftCacheEntity
                contentDescription = "${nftInfo.name} NFT Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                error = painterResource(id = android.R.drawable.ic_menu_gallery)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // NFT Name
            Text(
                text = nftInfo.name, // Use name from NftCacheEntity
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // NFT Description
            Text(
                text = nftInfo.description ?: "", // Use description from NftCacheEntity, provide default
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // View Button
            Button(
                onClick = { onViewClick(nftInfo.viewUrl) }, // Pass viewUrl from NftCacheEntity
                modifier = Modifier.align(Alignment.End),
                enabled = nftInfo.viewUrl != null // Disable button if URL is null
                // colors = ButtonDefaults.buttonColors() // Use default or xianButtonColors
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "View NFT", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("View")
            }
        }
    }
}
// --- End of NftItem Composable ---

// --- XnsNameItem Composable ---
@Composable
fun XnsNameItem(
    username: String,
    remainingDays: Long?, // Add remainingDays parameter
    navController: NavController // Add NavController parameter
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() } // Needed if showing snackbar on error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // .padding(vertical = 8.dp) // Padding is handled by LazyVerticalGrid spacing
            .height(300.dp), // Set the same fixed height as NftItem
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // Use similar background as NFTItem
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // Fill the fixed height
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, // Center content
            verticalArrangement = Arrangement.Center // Center content vertically
        ) {
            Icon(
                imageVector = Icons.Default.Person, // Use a generic person/user icon
                contentDescription = "XNS Name Icon",
                modifier = Modifier
                    .size(60.dp) // Adjusted size
                    .padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary // Use primary color for the icon
            )
            Text(
                text = username,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp, // Adjusted font size
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            // Optional: Add a small label
            Text(
                text = "XNS Name",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Display Remaining Days
            if (remainingDays != null) {
                Text(
                    text = "Expires in $remainingDays days",
                    fontSize = 12.sp,
                    color = if (remainingDays < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, // Highlight if expiring soon
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                 // Optional: Show placeholder or nothing if days are null (shouldn't happen for valid names)
                 Spacer(modifier = Modifier.height(18.dp)) // Keep spacing consistent
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes the button to the bottom

            // Button to open XNS Domains link
            Button(
                onClick = {
                    val url = "https://xns.domains/?name=$username"
                    try {
                        // URL Encode the URL before passing it as a navigation argument
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        // Navigate to the internal WebBrowserScreen
                        navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                    } catch (e: Exception) {
                        android.util.Log.e("XnsNameItem", "Failed to encode or navigate to URL: $url", e)
                        coroutineScope.launch {
                            // Consider showing a snackbar or toast
                            // snackbarHostState.showSnackbar("Could not open link")
                            android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally) // Center button
            ) {
                Icon(Icons.Default.Language, contentDescription = "View", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("View")
            }
        }
    }
}
// --- End of XnsNameItem Composable ---


/**
 * Composable function to display a single local transaction record.
 */
@Composable
fun TransactionRecordItem(record: LocalTransactionRecord) {
    val formatter = remember { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault()) }
    val formattedTimestamp = remember(record.timestamp) { formatter.format(java.time.Instant.ofEpochMilli(record.timestamp)) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.type, // "Sent" or "Received"
                    fontWeight = FontWeight.Bold,
                    color = if (record.type == "Sent") Color(0xFFE57373) else Color(0xFF81C784), // Red for Sent, Green for Received
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedTimestamp,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${if (record.type == "Sent") "-" else "+"}${record.amount} ${record.symbol}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (record.type == "Sent" && record.recipient != null) {
                Text("To: ${record.recipient.take(8)}...${record.recipient.takeLast(6)}", fontSize = 12.sp)
            }
            
            // TODO: Add 'From' if needed for received transactions (requires storing sender)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                 Text(
                    text = "Tx: ${record.txHash.take(10)}...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary, // Change color to indicate link
                    textDecoration = TextDecoration.Underline, // Add underline
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val url = "https://explorer.xian.org/tx/${record.txHash}"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("WalletScreen", "Failed to open URL: $url", e)
                                // Optionally show a toast or snackbar on failure
                                android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                )
                // IconButton removed
            }
        }
    }
}

