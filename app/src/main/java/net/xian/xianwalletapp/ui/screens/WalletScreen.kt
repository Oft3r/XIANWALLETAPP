package net.xian.xianwalletapp.ui.screens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import android.util.Log
import androidx.compose.animation.animateColorAsState // Added import
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.unit.IntOffset  // Added import for IntOffset
import kotlin.math.roundToInt  // Added import for roundToInt

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
import androidx.compose.material.icons.filled.Edit // Import for Edit icon
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download // Added import
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Visibility // For View icon
import androidx.compose.material.icons.filled.VisibilityOff // For Hide icon
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Build // Import for Build icon
import androidx.compose.material.icons.filled.Person // Import Person icon
import androidx.compose.material.icons.filled.ArrowDropDown // Import for dropdown arrow down
import androidx.compose.material.icons.filled.ArrowDropUp // Import for dropdown arrow up
import androidx.compose.material.icons.filled.HourglassEmpty // Import for hourglass icon
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.DismissValue
import androidx.compose.material.DismissDirection
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.saveable.rememberSaveable // Import rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import for state collection
import androidx.lifecycle.viewmodel.compose.viewModel // Import for getting ViewModel
import androidx.lifecycle.SavedStateHandle // Import SavedStateHandle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import coil.compose.AsyncImage
import coil.ImageLoader // For cached image loading
import android.content.Intent
import android.net.Uri
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.network.NftInfo
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.workers.scheduleTransactionMonitor // Add WorkManager import
import net.xian.xianwalletapp.workers.restartTransactionMonitor // Add restart function import
import kotlinx.coroutines.launch
import androidx.compose.material.ExperimentalMaterialApi
import net.xian.xianwalletapp.data.db.NftCacheEntity
// Use specific import for LocalTransactionRecord from data package
import net.xian.xianwalletapp.data.LocalTransactionRecord
import net.xian.xianwalletapp.ui.components.NftItem // Keep this import
import net.xian.xianwalletapp.ui.components.XnsNameItem // Keep this import
import net.xian.xianwalletapp.ui.components.TransactionRecordItem // Keep this import
import net.xian.xianwalletapp.ui.components.XianBottomNavBar
import net.xian.xianwalletapp.ui.components.BouncingDotsLoader
import net.xian.xianwalletapp.ui.components.ManageTokenList // Import the new component
import net.xian.xianwalletapp.ui.components.SmallBouncingDotsLoader
import net.xian.xianwalletapp.ui.components.LargeBouncingDotsLoader
// import net.xian.xianwalletapp.ui.theme.XianButtonType // Remove duplicate
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant

import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

// Remove duplicate import if present, ensure only one remains
// import net.xian.xianwalletapp.data.LocalTransactionRecord
import net.xian.xianwalletapp.data.TransactionHistoryManager // Added
import net.xian.xianwalletapp.ui.theme.XianButtonType // Keep one import
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel // Import ViewModel
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModelFactory // Import ViewModelFactory
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModel // Import NavigationViewModel
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModelFactory // Import NavigationViewModelFactory
import net.xian.xianwalletapp.ui.viewmodels.PredefinedToken // Import PredefinedToken data class
import net.xian.xianwalletapp.ui.components.XianBottomNavBar // Import our new navigation component
import androidx.compose.foundation.border
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
// NftCacheEntity already imported above
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.DpOffset

/**
 * Main wallet screen showing token balances and actions
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(
    navController: NavController,
    walletManager: WalletManager, // Keep for ViewModel creation
    networkService: XianNetworkService, // Keep for ViewModel creation
    // Obtain ViewModel instances - now passed as parameter to share with other screens
    viewModel: WalletViewModel,
    // Initialize NavigationViewModel for persistent navigation state
    navigationViewModel: NavigationViewModel = viewModel(
        factory = NavigationViewModelFactory(SavedStateHandle()) // Pass empty SavedStateHandle
    )
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()    // --- Collect State from ViewModel ---
    val publicKey by viewModel.publicKey.collectAsStateWithLifecycle() // Changed to collect from StateFlow
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    val poopPrice by viewModel.poopPrice.collectAsStateWithLifecycle() // Collect POOP price state
    val xtfuPrice by viewModel.xtfuPrice.collectAsStateWithLifecycle() // Collect XTFU price state
    val xarbPrice by viewModel.xarbPrice.collectAsStateWithLifecycle() // Collect XARB price state
    val activeWalletName by viewModel.activeWalletName.collectAsStateWithLifecycle()
    
    // Special handling for XIAN price - only load once at startup, not during refresh
    // Store the first non-null price we receive
    var staticXianPrice by remember { mutableStateOf<Float?>(null) }
      // Ensure proper navigation state when returning to the wallet screen
    LaunchedEffect(Unit) {
        // Sync navigation with wallet route (index 0)
        navigationViewModel.syncSelectedItemWithRoute("wallet")
    }
    
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

    // --- Collect Transaction History State from ViewModel ---
    val transactionHistory by viewModel.transactionHistory.collectAsStateWithLifecycle()
    val isTransactionHistoryLoading by viewModel.isTransactionHistoryLoading.collectAsStateWithLifecycle()
    val transactionHistoryError by viewModel.transactionHistoryError.collectAsStateWithLifecycle()    // --- Local UI State (Dialogs, Snackbar, etc.) ---
    var showAddTokenDialog by remember { mutableStateOf(false) }
    var newTokenContract by remember { mutableStateOf("") }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    // Removed isEditMode state - now using separate Manage tab
    
    // State for NFTs
    var showNftDropdown by remember { mutableStateOf(false) } // Control dropdown visibility    // State for Local Activity
    // REMOVE these local states as they are now handled by ViewModel
    // var transactionHistory by remember { mutableStateOf<List<LocalTransactionRecord>>(emptyList()) }
    // var isHistoryLoading by remember { mutableStateOf(false) }
    
    // Estado para mostrar/ocultar la barra inferior según el scroll en Collectibles
    var showBottomBar by remember { mutableStateOf(true) }
    var lastCollectiblesScrollIndex by remember { mutableStateOf(0) }
    var lastCollectiblesScrollOffset by remember { mutableStateOf(0) }
    
    // State for managing tokens mode
    var isManageMode by remember { mutableStateOf(false) }

    // Removed edit mode LaunchedEffect - now using separate Manage tab
    
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, // Hacer la barra transparente
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // NFT Image Preview Box (Clickable) & Dropdown
                        Box {
                            // Use displayedNftInfo (NftCacheEntity?)
                            if (displayedNftInfo != null) {
                                AsyncImage(
                                    model = displayedNftInfo?.imageUrl, // Use imageUrl from NftCacheEntity
                                    imageLoader = viewModel.getImageLoader(), // Use the custom image loader
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
                                                    imageLoader = viewModel.getImageLoader(), // Use the custom image loader
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
                            color = XianPrimaryVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Connection status indicator
                    Row(                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // Status text
                        Text(
                            text = if (isNodeConnected) "Connected" else "Disconnected",
                            fontSize = 12.sp,
                            color = if (isNodeConnected) 
                                Color.White // Keeping text white when connected
                            else 
                                Color(0xFFF44336) // Red for disconnected
                        )                        
                        // Status indicator dot
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(8.dp)
                                .background(
                                    color = if (isNodeConnected) 
                                        Color.Green // Changed to green for connected status
                                    else
                                        Color(0xFFF44336), // Red for disconnected
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
        // Remove floatingActionButton parameter here
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                XianBottomNavBar(
                    navController = navController,
                    navigationViewModel = navigationViewModel
                )
            }
        }) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(false), // Never show default indicator
                onRefresh = {
                    viewModel.refreshData()
                    // Restart transaction monitoring on refresh
                    restartTransactionMonitor(context)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // XIAN Balance Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp, max = 190.dp) // Set both min and max height constraints
                        .padding(bottom = 16.dp),                    border = BorderStroke(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(colors = listOf(XianPrimary, XianPrimaryVariant)) // Use new teal palette
                    )
                    // Removed background color
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 16.dp), // Keep padding as is
                        horizontalAlignment = Alignment.CenterHorizontally, // Center items horizontally
                        verticalArrangement = Arrangement.Top // Changed from Center to Top to allow manual spacing
                    ) {
                        // Add spacing at the top to push content lower
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Label with hourglass loading indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 0.dp) // Adjusted to ensure alignment with balance
                            ) {
                                Text(
                                    text = activeWalletName?.takeIf { it.isNotBlank() } ?: "My Wallet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            
                            // Hourglass loading indicator to the right of wallet name
                            if (isLoading) {
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                var rotation by remember { mutableStateOf(0f) }
                                
                                // Continuous rotation while loading
                                LaunchedEffect(isLoading) {
                                    while (isLoading) {
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
                                    contentDescription = "Loading",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(animatedRotation),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Use a larger spacer between label and balance for better visual balance
                        Spacer(modifier = Modifier.height(15.dp))
                        
                        // Calculate total balance across all tokens
                        if (staticXianPrice == null) {
                            SmallBouncingDotsLoader(
                                modifier = Modifier.size(24.dp),
                                dotColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            // Calculate total balance using:
                            // 1. XIAN: amount × price
                            // 2. POOP: amount × price in XIAN × XIAN price
                            // 3. XTFU: amount × price in XIAN × XIAN price
                            // 4. USDC: direct USD value
                              // Store delegated properties in local variables to avoid smart cast issues
                            val currentXianPrice = staticXianPrice ?: 0f
                            val currentPoopPrice = poopPrice
                            val currentXtfuPrice = xtfuPrice
                            val currentXarbPrice = xarbPrice
                              val xianUsdValue = balanceMap["currency"]?.let { it * currentXianPrice } ?: 0f
                            val poopUsdValue = if (currentPoopPrice != null && balanceMap["con_poop_coin"] != null) {
                                balanceMap["con_poop_coin"]!! * currentPoopPrice * currentXianPrice
                            } else 0f
                            val xtfuUsdValue = if (currentXtfuPrice != null && balanceMap["con_xtfu"] != null) {
                                balanceMap["con_xtfu"]!! * currentXtfuPrice * currentXianPrice
                            } else 0f
                            val xarbUsdValue = if (currentXarbPrice != null && balanceMap["con_xarb"] != null) {
                                balanceMap["con_xarb"]!! * currentXarbPrice * currentXianPrice
                            } else 0f
                            val usdcValue = balanceMap["con_usdc"] ?: 0f // Direct USD value
                            
                            val totalBalance = xianUsdValue + poopUsdValue + xtfuUsdValue + xarbUsdValue + usdcValue
                            // var isBalanceVisible by remember { mutableStateOf(true) } // Replaced by ViewModel state
                            val isBalanceVisible by viewModel.isBalanceVisible.collectAsStateWithLifecycle()

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isBalanceVisible) "$%.2f".format(totalBalance) else "$***.**",
                                    fontSize = 55.sp, // Set specific larger font size
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                                IconButton(onClick = { viewModel.toggleBalanceVisibility() }) { // Call ViewModel function
                                    Icon(
                                        imageVector = if (isBalanceVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                } // End of Card

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

                // Tabs for Tokens/NFTs/Activity
                var selectedTabIndex by remember { mutableStateOf(0) }
                // When selectedTabIndex changes, update the NavigationViewModel
                // REMOVED: This LaunchedEffect was incorrectly updating the main navigation state
                // based on internal tab selection within WalletScreen.
                // WalletScreen as a whole corresponds to the "Portfolio" (index 0) main navigation item.
                // Internal tab changes should not affect the main bottom bar's selected item.
                /*
                LaunchedEffect(selectedTabIndex) {
                    when (selectedTabIndex) {
                        0 -> navigationViewModel.setSelectedNavItem(0) // Use setSelectedNavItem
                        1 -> navigationViewModel.setSelectedNavItem(1) // Use setSelectedNavItem
                        2 -> navigationViewModel.setSelectedNavItem(2) // Use setSelectedNavItem
                    }
                }
                */

                // Sync selectedTabIndex from NavigationViewModel when the screen is first composed or recomposed
                // This ensures tab selection is persistent across navigation events if needed
                LaunchedEffect(navigationViewModel.selectedNavItem) { // Observe selectedNavItem
                    selectedTabIndex = when (navigationViewModel.selectedNavItem.value) { // Access value of StateFlow
                        0 -> 0 // "wallet_tokens" -> 0
                        1 -> 1 // "wallet_collectibles" -> 1
                        2 -> 2 // "wallet_activity" -> 2
                        else -> selectedTabIndex // Keep current if no match or initial state
                    }
                }

                // Row containing TabRow and Edit button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        indicator = { tabPositions ->
                            // Prevent crash by checking bounds
                            if (selectedTabIndex < tabPositions.size) {
                                Box(
                                    Modifier
                                        .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        },
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Tokens") },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = {
                                selectedTabIndex = 1
                                isManageMode = false
                            },
                            text = { Text("Items") },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = {
                                selectedTabIndex = 2
                                isManageMode = false
                            },
                            text = { Text("Activity") },
                            modifier = Modifier.clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Edit Button moved here - only show when in Tokens tab or manage mode
                    if (selectedTabIndex == 0 || isManageMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isManageMode = !isManageMode // Toggle manage mode
                                if (!isManageMode) {
                                    showBottomBar = true // Show bottom bar when exiting manage mode
                                }
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .width(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isManageMode) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                }
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Manage Tokens",
                                modifier = Modifier.size(20.dp),
                                tint = if (isManageMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                // REMOVE LaunchedEffect for selectedTabIndex == 2 that loads history manually
                // LaunchedEffect(selectedTabIndex) {
                //      if (selectedTabIndex == 2) {
                //         // Load transaction history when tab is selected
                //         isHistoryLoading = true
                //         val historyManager = TransactionHistoryManager(context)
                //         transactionHistory = historyManager.loadRecords()
                //         android.util.Log.d("WalletScreen", "Loaded ${transactionHistory.size} history records.")
                //         isHistoryLoading = false
                //     }
                // }

                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content based on selected tab and manage mode
                when {
                    isManageMode -> {
                        // Manage Tokens mode - Show ManageTokenList content inline
                        ManageTokenList(
                            viewModel = viewModel,
                            onBackClick = {
                                isManageMode = false // Exit manage mode
                                showBottomBar = true // Reset bottom bar visibility when exiting manage mode
                            },
                            showBottomBar = showBottomBar,
                            onShowBottomBarChange = { showBottomBar = it }
                        )
                    }
                    selectedTabIndex == 0 -> {
                        // Tokens tab
                        when {
                            // ONLY show loading indicator when tokens list is empty (meaning we're likely
                            // in initial state or we just added a first token)
                            isLoading && tokens.isEmpty() -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    LargeBouncingDotsLoader()
                                }
                            }
                            tokens.isEmpty() -> {
                                // Empty state inside SwipeRefresh (already is, but make sure it fills space)
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No tokens added yet.\nClick the + button to add a token.",
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }                            else -> {
                                // Always show the list when we have tokens, regardless of isLoading state
                                // State variables for scroll tracking in tokens list
                                var lastTokensScrollIndex by remember { mutableStateOf(0) }
                                var lastTokensScrollOffset by remember { mutableStateOf(0) }
                                val tokensListState = rememberLazyListState()
                                
                                // Scroll behavior effect for tokens list
                                LaunchedEffect(tokensListState.firstVisibleItemIndex, tokensListState.firstVisibleItemScrollOffset) {
                                    val index = tokensListState.firstVisibleItemIndex
                                    val offset = tokensListState.firstVisibleItemScrollOffset
                                    if (index > lastTokensScrollIndex || (index == lastTokensScrollIndex && offset > lastTokensScrollOffset + 10)) {
                                        // Scroll down (hide bottom bar)
                                        if (showBottomBar) showBottomBar = false
                                    } else if (index < lastTokensScrollIndex || (index == lastTokensScrollIndex && offset < lastTokensScrollOffset - 10)) {
                                        // Scroll up (show bottom bar)
                                        if (!showBottomBar) showBottomBar = true
                                    }
                                    lastTokensScrollIndex = index
                                    lastTokensScrollOffset = offset
                                }
                                  LazyColumn(
                                    state = tokensListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp) // Add padding for bottom navigation bar
                                ) {
                                    items(tokens) { contract ->
                                        val tokenInfo = tokenInfoMap[contract]
                                        val balance = balanceMap[contract] ?: 0f
                                          TokenItem(
                                            contract = contract,
                                            name = tokenInfo?.name ?: contract,
                                            symbol = tokenInfo?.symbol ?: "",
                                            logoUrl = tokenInfo?.logoUrl,
                                            balance = balance,
                                            xianPrice = if (contract == "currency") xianPrice else null,
                                            poopPrice = if (contract == "con_poop_coin") poopPrice else null, // Pasar el precio de POOP
                                            xtfuPrice = if (contract == "con_xtfu") xtfuPrice else null, // Pasar el precio de XTFU
                                            xarbPrice = if (contract == "con_xarb") xarbPrice else null, // Pasar el precio de XARB
                                            imageLoader = viewModel.getImageLoader(), // Pass the custom image loader
                                            onSendClick = {
                                                navController.navigate(
                                                    "${XianDestinations.SEND_TOKEN}?${XianNavArgs.TOKEN_CONTRACT}=$contract&${XianNavArgs.TOKEN_SYMBOL}=${tokenInfo?.symbol ?: ""}"
                                                )
                                            },
                                            onReceiveClick = {
                                                navController.navigate(XianDestinations.RECEIVE_TOKEN)
                                            },
                                            onRemoveClick = null, // Remove edit functionality - now handled in Manage tab
                                            onCardClick = {
                                                navController.navigate(
                                                    "${XianDestinations.TOKEN_DETAIL}?${XianNavArgs.TOKEN_CONTRACT}=$contract&${XianNavArgs.TOKEN_SYMBOL}=${tokenInfo?.symbol ?: ""}"
                                                )
                                            }
                                        )
                                    }
                                      // Add capsule button at the end of the list
                                }
                            }
                        }
                    }
                    selectedTabIndex == 1 -> {
                        // Collectibles tab (NFTs and XNS Names)

                        // *** ADD LOGGING HERE ***
                        Log.d("WalletScreen", "Items Tab: nftList size = ${nftList.size}, ownedXnsNames size = ${ownedXnsNames.size}")

                        // Show loading indicator only on initial load when both lists are empty
                        if (isNftLoading && nftList.isEmpty() && ownedXnsNames.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LargeBouncingDotsLoader()
                            }
                        } else if (nftList.isEmpty() && ownedXnsNames.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No Collectibles found.", // Updated text
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else { // Combine NFTs and XNS names for the grid
                            val totalItems = nftList.size + ownedXnsNames.size

                            val collectiblesGridState = rememberLazyGridState()
                            LaunchedEffect(collectiblesGridState.firstVisibleItemIndex, collectiblesGridState.firstVisibleItemScrollOffset) {
                                val index = collectiblesGridState.firstVisibleItemIndex
                                val offset = collectiblesGridState.firstVisibleItemScrollOffset
                                if (index > lastCollectiblesScrollIndex || (index == lastCollectiblesScrollIndex && offset > lastCollectiblesScrollOffset + 10)) {
                                    // Scroll hacia abajo (usuario baja la lista)
                                    if (showBottomBar) showBottomBar = false
                                } else if (index < lastCollectiblesScrollIndex || (index == lastCollectiblesScrollIndex && offset < lastCollectiblesScrollOffset - 10)) {
                                    // Scroll hacia arriba (usuario sube la lista)
                                    if (!showBottomBar) showBottomBar = true
                                }
                                lastCollectiblesScrollIndex = index
                                lastCollectiblesScrollOffset = offset
                            }

                            LazyVerticalGrid(
                                state = collectiblesGridState,
                                columns = GridCells.Fixed(3), // Cambiado de 2 a 3 columnas
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp), // Reducido de 8dp a 6dp para mejor ajuste
                                verticalArrangement = Arrangement.spacedBy(6.dp)    // Reducido de 8dp a 6dp para mejor ajuste
                            ) {
                                // Render NFTs first
                                items(nftList) { nft: NftCacheEntity -> // Keep explicit type
                                    NftItem(
                                        nftInfo = nft,
                                        onViewClick = { url: String? -> // Add explicit type for url
                                            url?.let { urlString: String -> // Explicitly type 'urlString'
                                                try {
                                                    // Encode the URL before navigating
                                                    val encodedUrl = URLEncoder.encode(urlString, StandardCharsets.UTF_8.toString())
                                                    // Navigate to the in-app browser screen
                                                    navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                                                } catch (e: Exception) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Could not open URL: Invalid format")
                                                        Log.e("WalletScreen", "Error encoding or navigating to URL: $urlString", e)
                                                    }
                                                }
                                            } ?: run {
                                                // Handle case where URL is null, if necessary
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Cannot open: URL is missing")
                                                }
                                            }
                                        }
                                    )
                                }

                                // Render XNS Names after NFTs
                                items(ownedXnsNames) { xnsName: String ->
                                    val expiration = xnsNameExpirations[xnsName]
                                    // Calculate remaining days from expiration Instant
                                    val remainingDays = expiration?.let { timestamp: Long ->
                                        val now = java.time.Instant.now()
                                        // Use Instant.ofEpochSecond to convert Long to Instant
                                        val expirationInstant = java.time.Instant.ofEpochSecond(timestamp)
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
                    selectedTabIndex == 2 -> {
                        // Local Activity tab - Now uses ViewModel states
                        var lastActivityScrollIndex by remember { mutableStateOf(0) }
                        var lastActivityScrollOffset by remember { mutableStateOf(0) }
                        val activityListState = rememberLazyListState()
                        LaunchedEffect(activityListState.firstVisibleItemIndex, activityListState.firstVisibleItemScrollOffset) {
                            val index = activityListState.firstVisibleItemIndex
                            val offset = activityListState.firstVisibleItemScrollOffset
                            if (index > lastActivityScrollIndex || (index == lastActivityScrollIndex && offset > lastActivityScrollOffset + 10)) {
                                // Scroll down (hide bar)
                                if (showBottomBar) showBottomBar = false
                            } else if (index < lastActivityScrollIndex || (index == lastActivityScrollIndex && offset < lastActivityScrollOffset - 10)) {
                                // Scroll up (show bar)
                                if (!showBottomBar) showBottomBar = true
                            }
                            lastActivityScrollIndex = index
                            lastActivityScrollOffset = offset
                        }

                        if (isTransactionHistoryLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LargeBouncingDotsLoader()
                            }
                        } else if (transactionHistoryError != null) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Error: $transactionHistoryError",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadTransactionHistory(force = true) }) {
                                    Text("Retry")
                                }
                            }
                        } else if (transactionHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No transaction history found.", // Updated message
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            // Filter out consecutive duplicates and group by date
                            val distinctTransactionHistory = transactionHistory.fold(mutableListOf<LocalTransactionRecord>()) { acc, record ->
                                if (acc.isEmpty() || acc.last() != record) {
                                    acc.add(record)
                                }
                                acc
                            }

                            // Group transactions by date
                            val groupedTransactions = distinctTransactionHistory
                                .groupBy { record ->
                                    java.time.Instant.ofEpochMilli(record.timestamp)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate()
                                }
                                .toSortedMap(compareByDescending { it })

                            LazyColumn(
                                state = activityListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                groupedTransactions.forEach { (date, records) ->
                                    item {
                                        // Date header
                                        Text(
                                            text = date.format(java.time.format.DateTimeFormatter
                                                .ofPattern("MMMM d")
                                                .withLocale(java.util.Locale.ENGLISH)),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(records) { record ->
                                        TransactionRecordItem(record = record, navController = navController)
                                    }
                                }
                            }
                        }
                    }
                }
           }
           } // Close SwipeRefresh
       } // Close Box
   } // Close Scaffold
      // Add token dialog
    if (showAddTokenDialog) {
        var contractAddress by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        val predefinedTokens by viewModel.predefinedTokens.collectAsStateWithLifecycle()
        var textFieldWidthPx by remember { mutableStateOf(0) } // State for pixel width
        val density = LocalDensity.current // Get density in the composable scope

        AlertDialog(
            onDismissRequest = { showAddTokenDialog = false },
            title = { Text("Add Token") },
            text = {
                Column {
                    Text("Select a predefined token or enter a contract address manually.")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Box to anchor the dropdown and measure the TextField
                    Box {
                        OutlinedTextField(
                            value = contractAddress,
                            onValueChange = { contractAddress = it },
                            label = { Text("Token Contract Address") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    val widthInPixels = coordinates.size.width
                                    // Update state only if the width actually changes
                                    if (textFieldWidthPx != widthInPixels) {
                                        textFieldWidthPx = widthInPixels
                                        Log.d("DropdownWidth", "TextField positioned. Pixel Width: $widthInPixels")
                                    }
                                },
                            singleLine = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                    contentDescription = "Toggle Predefined Tokens",
                                    modifier = Modifier.clickable { expanded = !expanded }
                                )
                            }
                        )

                        // Dropdown Menu
                        DropdownMenu(
                            expanded = expanded && predefinedTokens.isNotEmpty(), // Only expand if list not empty
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                // Calculate Dp width directly using density and pixel state
                                // Use a fallback width if measurement hasn't happened yet
                                .requiredWidth(
                                    with(density) {
                                        if (textFieldWidthPx > 0) {
                                            textFieldWidthPx.toDp()
                                        } else {
                                            // Provide a sensible default minimum width if not measured
                                            // Using TextFieldDefaults.MinWidth might be appropriate
                                            TextFieldDefaults.MinWidth
                                        }
                                    }
                                )
                                .heightIn(max = 250.dp) // Limit dropdown height
                                .background(MaterialTheme.colorScheme.surface) // Ensure background
                            // No offset needed if anchored correctly by Box
                        ) {
                            predefinedTokens.forEach { token ->
                                DropdownMenuItem(                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = when {
                                                    token.contract == "con_xarb" -> "file:///android_asset/xarb.jpg"
                                                    else -> token.logoUrl
                                                },
                                                imageLoader = viewModel.getImageLoader(), // Use the custom image loader
                                                contentDescription = "${token.name} logo",
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape) // Make logo circular
                                                    .background(Color.LightGray), // Placeholder background
                                                placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                                                error = painterResource(id = R.drawable.ic_launcher_foreground)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(token.name, fontWeight = FontWeight.Bold)
                                                Text(
                                                    token.contract,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        Log.d("WalletScreen", "Predefined token selected: ${token.name} (${token.contract})")
                                        viewModel.addTokenAndRefresh(token.contract)
                                        expanded = false
                                        showAddTokenDialog = false // Close dialog after selection
                                    }
                                )
                                Divider() // Add divider between items
                            }
                        }
                    } // End Box

                    Spacer(modifier = Modifier.height(8.dp)) // Space between dropdown/textfield and manual add button if needed
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contractAddress.isNotBlank()) {
                            Log.d("WalletScreen", "Manually adding token: $contractAddress")
                            viewModel.addTokenAndRefresh(contractAddress)
                            showAddTokenDialog = false
                        }
                        // Optionally show error if blank?
                    },
                    // Disable button if dropdown is expanded OR contract address is blank
                    enabled = !expanded && contractAddress.isNotBlank()
                ) {
                    Text("Add Manually")
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
    xianPrice: Float? = null, // Para token XIAN - precio en USD
    poopPrice: Float? = null, // Añadir precio de POOP en XIAN
    xtfuPrice: Float? = null, // Añadir precio de XTFU en XIAN
    xarbPrice: Float? = null, // Añadir precio de XARB en XIAN
    imageLoader: ImageLoader, // Add ImageLoader parameter
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null, // Hacer opcional para el modo edición
    onCardClick: () -> Unit = {} // Add card click handler
) {
    // Use different UI for XIAN currency (contract == "currency")
    if (contract == "currency") {
        // Use SwipeableXianCard for XIAN token only
        SwipeableXianCard(
            name = name,
            symbol = symbol,
            logoUrl = logoUrl,
            balance = balance,
            xianPrice = xianPrice,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick,
            onCardClick = onCardClick
        )
    } else {
        // Regular card UI for other tokens
        SwipeableTokenCard(
            contract = contract, // Pass the contract here
            name = name,
            symbol = symbol,
            logoUrl = logoUrl,
            balance = balance,
            usdValue = if (contract == "con_poop_coin") null else null, // No mostrar USD para otros tokens por ahora
            xianPrice = when (contract) {
                "con_poop_coin" -> poopPrice
                "con_xtfu" -> xtfuPrice
                "con_xarb" -> xarbPrice
                else -> null
            },
            imageLoader = imageLoader, // Pass down the loader
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick,
            onRemoveClick = onRemoveClick,
            onCardClick = onCardClick
        )
    }
}

/**
 * A card specifically for the XIAN token
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableXianCard(
    name: String,
    symbol: String,
    logoUrl: String?,
    balance: Float,
    xianPrice: Float? = null,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onCardClick: () -> Unit = {} // Add card click handler
) {
    // Outer Box - Consolidate pointer input here
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(CardDefaults.shape)
            .clickable { onCardClick() } // Keep clickable for card click
    ) {
        // The card with NO horizontal swipe handling - will allow parent scroll to work
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(8.dp)),
             shape = RoundedCornerShape(8.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
             elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // Main card content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    // REMOVED: pointerInput from Column
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Token icon using AsyncImage - ALWAYS use xian_logo for currency
                    Image(
                        painter = painterResource(id = R.drawable.xian_logo), // Use local resource directly
                        contentDescription = "$name Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Inside
                    )

                    // Token details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = "XIAN Currency",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = symbol,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }

                    // Token balance and USD value
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.4f)) { // Give some weight to prevent overlap
                        Text(
                            text = "%.1f".format(balance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (xianPrice != null) {
                            // Mostrar el precio en USD para XIAN con formato "$"
                            Text(
                                text = "$%.6f".format(xianPrice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8BC34A), // Verde limón más oscuro para el precio en USD
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // REMOVED: Hint text for gestures
            }
        }
    }
}

/**
 * A unified swipeable card for all tokens with gesture animations, USD value display and removal option
 */
@Composable
fun SwipeableTokenCard(
    name: String,
    symbol: String,
    logoUrl: String?,
    balance: Float,
    usdValue: Float? = null,
    xianPrice: Float? = null, // Añadir parámetro xianPrice para el token POOP
    imageLoader: ImageLoader, // Add ImageLoader parameter
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onRemoveClick: (() -> Unit)? = null,
    onCardClick: () -> Unit = {}, // Add card click handler
    contract: String // Add contract parameter here
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onCardClick() } // Keep clickable for card click
    ) {
        // The card with NO horizontal swipe handling - will allow parent scroll to work
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface, // Fully opaque
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Card contents - TOP ROW (Icon, Name, Balance)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // REMOVED: Add horizontal swipe handling ONLY to the token info row
                        ,
                    verticalAlignment = Alignment.CenterVertically
                ) {                    // Token icon using AsyncImage
                    AsyncImage(
                        model = when {
                            contract == "con_xarb" -> "file:///android_asset/xarb.jpg"
                            else -> logoUrl
                        },
                        imageLoader = imageLoader, // Use the custom image loader
                        contentDescription = "$name Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Inside,
                        error = if (contract == "currency") painterResource(id = R.drawable.xian_logo) else painterResource(id = R.drawable.ic_question_mark), // Use xian_logo for currency, question mark otherwise
                        placeholder = if (contract == "currency") painterResource(id = R.drawable.xian_logo) else painterResource(id = R.drawable.ic_question_mark) // Use xian_logo for currency, question mark otherwise
                    )

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

                    // Token balance and USD value
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.1f".format(balance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (usdValue != null) {
                            Text(
                                text = "$%.2f".format(usdValue),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else if (xianPrice != null) {
                            // Mostrar el precio en XIAN para POOP y XTFU con formato "X*"
                            Text(
                                text = "X*%.6f".format(xianPrice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8BC34A), // Verde limón más oscuro para el precio
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                
                // Update the hint text to better reflect the longer swipe distance
                Spacer(modifier = Modifier.height(8.dp))
                
                // Row that contains both the swipe text and remove button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End // Changed from SpaceBetween to End
                ) {
                    // Add empty spacer if there\'s no remove button to keep swipe text centered
                    if (onRemoveClick == null) {
                        Spacer(modifier = Modifier.width(20.dp))
                    }

                    // Only show remove button if provided
                    if (onRemoveClick != null) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .border(1.dp, MaterialTheme.colorScheme.error, CircleShape)
                                .clickable(onClick = onRemoveClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−", // Unicode minus sign
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
