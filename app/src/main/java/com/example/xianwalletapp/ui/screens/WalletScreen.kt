package com.example.xianwalletapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.xianwalletapp.R
import com.example.xianwalletapp.navigation.XianDestinations
import com.example.xianwalletapp.navigation.XianNavArgs
import com.example.xianwalletapp.network.TokenInfo
import coil.compose.AsyncImage // For Coil image loading - TODO: Add Coil dependency
import android.content.Intent
import android.net.Uri
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.network.NftInfo
import com.example.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.launch
import androidx.compose.material.ExperimentalMaterialApi
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.xianwalletapp.data.LocalTransactionRecord // Added
import com.example.xianwalletapp.data.TransactionHistoryManager // Added
import com.example.xianwalletapp.ui.theme.XianButtonType
import com.example.xianwalletapp.ui.theme.xianButtonColors

/**
 * Main wallet screen showing token balances and actions
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val publicKey = walletManager.getPublicKey() ?: ""
    
    // State for tokens and balances
    var tokens by remember { mutableStateOf(walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })) }
    var tokenInfoMap by remember { mutableStateOf<Map<String, TokenInfo>>(emptyMap()) }
    var balanceMap by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddTokenDialog by remember { mutableStateOf(false) }
    var newTokenContract by remember { mutableStateOf("") }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State for NFTs
    var nftList by remember { mutableStateOf<List<com.example.xianwalletapp.network.NftInfo>>(emptyList()) } // Assuming NftInfo data class exists
    var isNftLoading by remember { mutableStateOf(false) }
    var transactionHistory by remember { mutableStateOf<List<LocalTransactionRecord>>(emptyList()) } // Added
    var isHistoryLoading by remember { mutableStateOf(false) } // Added
    
    // Add a refresh trigger
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Track node connectivity
    var isNodeConnected by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    
    // Load token info and balances
    LaunchedEffect(tokens, refreshTrigger) {
        isLoading = true
        
        // Check node connectivity first
        isCheckingConnection = true
        isNodeConnected = networkService.checkNodeConnectivity()
        isCheckingConnection = false
        
        val newTokenInfoMap = mutableMapOf<String, TokenInfo>()
        val newBalanceMap = mutableMapOf<String, Float>()
        
        tokens.forEach { contract ->
            // Get token info
            val tokenInfo = networkService.getTokenInfo(contract)
            newTokenInfoMap[contract] = tokenInfo
            
            // Get token balance
            val balance = networkService.getTokenBalance(contract, publicKey)
            android.util.Log.d("WalletScreen", "Loaded balance for $contract: $balance")
            newBalanceMap[contract] = balance
        }
        
        tokenInfoMap = newTokenInfoMap
        tokenInfoMap = newTokenInfoMap
        balanceMap = newBalanceMap
        isLoading = false
    }
    
    // Check connectivity periodically
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(10000) // Initial delay
        while (true) {
            isCheckingConnection = true
            isNodeConnected = networkService.checkNodeConnectivity()
            isCheckingConnection = false
            kotlinx.coroutines.delay(30000) // Check every 30 seconds
        }
    }
    
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    onClick = { navController.navigate(XianDestinations.MESSENGER) },
                    icon = { Icon(Icons.Default.Send, contentDescription = "Messenger") },
                    label = { Text("Messenger") }
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
            state = rememberSwipeRefreshState(isLoading),
            onRefresh = {
                coroutineScope.launch {
                    tokens = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
                    refreshTrigger += 1  // Increment refresh trigger to force data reload
                }
            }
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
                    ) {
                        // Label
                        Text(
                            text = "XIAN Balance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp)) // Add space between label and balance

                        // Display balance or loading indicator
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            val xianBalance = balanceMap["currency"] ?: 0f
                            Text(
                                text = "%.8f".format(xianBalance), // Format to 8 decimal places
                                style = MaterialTheme.typography.headlineSmall, // Adjusted style for better visibility
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center // Center the balance text
                            )
                        }
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
                        text = { Text("NFTs") }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("Local Activity") }
                    )
                }

                // Load NFT data when NFT tab is selected
                LaunchedEffect(selectedTabIndex) {
                    if (selectedTabIndex == 1 && publicKey.isNotEmpty()) { // Ensure publicKey is available
                        isNftLoading = true
                        nftList = networkService.getNfts(publicKey) // Call the new network function
                        android.util.Log.d("WalletScreen", "Fetched ${nftList.size} NFTs")
                        isNftLoading = false
                    } else if (selectedTabIndex == 1 && publicKey.isEmpty()) {
                         android.util.Log.w("WalletScreen", "Cannot fetch NFTs, publicKey is empty.")
                         isNftLoading = false // Ensure loading stops if publicKey is missing
                    } else if (selectedTabIndex == 2) {
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
                                    
                                    TokenItem(
                                        contract = contract,
                                        name = tokenInfo?.name ?: contract,
                                        symbol = tokenInfo?.symbol ?: "",
                                        balance = balance,
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
                                                walletManager.removeToken(contract)
                                                tokens = walletManager.getTokenList().toList()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Token removed")
                                                }
                                            }
                                        }
                                    )
                                    
                                }
                            }
                        }
                    }
                    1 -> {
                        // NFTs tab
                        if (isNftLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (nftList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No NFTs found.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Use LazyColumn for NFT list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp) // Add padding for FAB
                            ) {
                                items(nftList) { nft ->
                                    NftItem(
                                        nftInfo = nft,
                                        onViewClick = { url ->
                                            // Open the NFT view URL in a browser
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("WalletScreen", "Failed to open URL: $url", e)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Could not open link")
                                                }
                                            }
                                        }
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
                                    Divider()
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
                            if (walletManager.addToken(newTokenContract)) {
                                tokens = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Token added")
                                }
                            }
                            newTokenContract = ""
                            showAddTokenDialog = false
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
    contract: String,
    name: String,
    symbol: String,
    balance: Float,
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
                // Token icon (placeholder)
                if (symbol.equals("XIAN", ignoreCase = true)) {
                    //Use XIAN logo for XIAN tokens
                    Image(
                        painter = painterResource(id = R.drawable.xian_white_logo),
                        contentDescription = "XIAN Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                } else {
                    // Default icon for other tokens
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (symbol.isNotEmpty()) symbol.take(1) else "?",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Token details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = symbol,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                
                // Token balance
                Text(
                    text = String.format("%.1f %s", balance, symbol),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
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


// --- NftItem Composable ---
@Composable
fun NftItem(
    nftInfo: NftInfo,
    onViewClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // Slightly different background
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // NFT Image
            AsyncImage(
                model = nftInfo.imageUrl,
                contentDescription = "${nftInfo.name} NFT Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp) // Adjust height as needed
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)), // Placeholder background
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                error = painterResource(id = android.R.drawable.ic_menu_gallery) // Use standard gallery icon as fallback
            )

            Spacer(modifier = Modifier.height(12.dp))

            // NFT Name
            Text(
                text = nftInfo.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))



// TransactionRecordItem moved below NftItem
            // NFT Description
            Text(
                text = nftInfo.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // View Button
            Button(
                onClick = { onViewClick(nftInfo.viewUrl) },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors() // TODO: Revert to xianButtonColors if definition is correct
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "View NFT", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("View")
            }
        }
    }
}
// --- End of NftItem Composable ---


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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                 IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(record.txHash))
                        coroutineScope.launch {
                            // Consider using a SnackbarHostState defined in WalletScreen if needed
                            // snackbarHostState.showSnackbar("Transaction ID copied")
                            android.widget.Toast.makeText(context, "Transaction ID copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(24.dp) // Smaller copy button
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Transaction ID",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.size(16.dp) // Smaller icon
                    )
                }
            }
        }
    }
}