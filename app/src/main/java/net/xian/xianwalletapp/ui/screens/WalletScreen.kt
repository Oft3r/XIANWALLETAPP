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
import java.util.Locale
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
import androidx.compose.material.icons.filled.Eco // Icono de planta/eco para Farm
import androidx.compose.material.icons.filled.Analytics // Icono para análisis de portafolio
import androidx.compose.material.icons.filled.Build // Import for Build icon
import androidx.compose.material.icons.filled.Person // Import Person icon
import androidx.compose.material.icons.filled.ArrowDropDown // Import for dropdown arrow down
import androidx.compose.material.icons.filled.ArrowDropUp // Import for dropdown arrow up
import androidx.compose.material.icons.filled.HourglassEmpty // Import for hourglass icon
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
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
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
 * Helper function to get background resource ID from background name
 */
private fun getBackgroundResourceId(backgroundName: String?): Int? {
    return when (backgroundName) {
        "wallpaper1" -> R.drawable.wallpaper1
        "wallpaper2" -> R.drawable.wallpaper2
        "wallpaper3" -> R.drawable.wallpaper3
        "wallpaper4" -> R.drawable.wallpaper4
        "wallpaper5" -> R.drawable.wallpaper5
        "wallpaper6" -> R.drawable.wallpaper6
        "wallpaper7" -> R.drawable.wallpaper7
        "wallpaper8" -> R.drawable.wallpaper8
        "dark" -> -1 // Special case for dark background
        else -> null
    }
}

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
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current    // --- Collect State from ViewModel ---
    val publicKey by viewModel.publicKey.collectAsStateWithLifecycle() // Changed to collect from StateFlow
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    val poopPrice by viewModel.poopPrice.collectAsStateWithLifecycle() // Collect POOP price state
    val xtfuPrice by viewModel.xtfuPrice.collectAsStateWithLifecycle() // Collect XTFU price state
    val xarbPrice by viewModel.xarbPrice.collectAsStateWithLifecycle() // Collect XARB price state
    val xwtPrice by viewModel.xwtPrice.collectAsStateWithLifecycle() // Collect XWT price state
    val slitherPrice by viewModel.slitherPrice.collectAsStateWithLifecycle() // Collect SLITHER price state
    val activeWalletName by viewModel.activeWalletName.collectAsStateWithLifecycle()
    val isBalanceVisible by viewModel.isBalanceVisible.collectAsStateWithLifecycle()
    val selectedCardBackground by viewModel.selectedCardBackground.collectAsStateWithLifecycle()
    // Portfolio snapshot for consistent total value
    val portfolioSnapshot by viewModel.portfolioSnapshot.collectAsStateWithLifecycle()
    
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
    // Replace snackbar with custom toast host
    // val snackbarHostState = remember { SnackbarHostState() }
    val toastHostState = net.xian.xianwalletapp.ui.components.rememberToastHostState()
    // Removed isEditMode state - now using separate Manage tab
    
    // State for Local Activity
    // REMOVE these local states as they are now handled by ViewModel
    // var transactionHistory by remember { mutableStateOf<List<LocalTransactionRecord>>(emptyList()) }
    // var isHistoryLoading by remember { mutableStateOf(false) }
    
    // Estado para mostrar/ocultar la barra inferior según el scroll en Collectibles
    var showBottomBar by remember { mutableStateOf(true) }
    var lastCollectiblesScrollIndex by remember { mutableStateOf(0) }
    var lastCollectiblesScrollOffset by remember { mutableStateOf(0) }
    
    // State for managing tokens mode
    var isManageMode by remember { mutableStateOf(false) }
    
    // State for wallet selector dropdown
    var showWalletDropdown by remember { mutableStateOf(false) }
    val availableWallets = remember(publicKey) { walletManager.getWalletPublicKeys() }

    // Estado para el efecto de compresión y rebote dinámico
    val swipeRefreshState = rememberSwipeRefreshState(false)
    
    // Calcular la escala basada en el progreso del swipe
    // swipeRefreshState.indicatorOffset nos da la posición del indicador
    val pullProgress = (swipeRefreshState.indicatorOffset / 200f).coerceIn(0f, 1f)
    val dynamicScale = 1f - (pullProgress * 0.05f) // Compresión máxima del 5%
    
    // Animación suave para la escala
    val animatedScale by animateFloatAsState(
        targetValue = dynamicScale,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 400f
        ),
        label = "DynamicCompressionScale"
    )

    // Removed edit mode LaunchedEffect - now using separate Manage tab
    
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    // Wallet Selector Dropdown with loading indicator outside - Container Box
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xFF252525), // Color un poco más claro que el oscuro original pero manteniendo el tono oscuro
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable { showWalletDropdown = true }
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(percent = 50),
                                        clip = false,
                                        ambientColor = XianPrimary.copy(alpha = 0.2f),
                                        spotColor = XianPrimaryVariant.copy(alpha = 0.25f)
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = activeWalletName?.takeIf { it.isNotBlank() } ?: "My Wallet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = XianPrimary // Cambiar al color verde característico
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (showWalletDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Wallet",
                                        modifier = Modifier.size(20.dp),
                                        tint = XianPrimary // También cambiar el color de la flecha
                                    )
                                }
                            }
                            
                            // Hourglass loading indicator outside the capsule
                            if (isLoading) {
                                Spacer(modifier = Modifier.width(12.dp))
                                
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
                                        .size(18.dp)
                                        .rotate(animatedRotation),
                                    tint = XianPrimary
                                )
                            }
                        }
                        
                        // Dropdown Menu con estilo personalizado - Ahora fuera del Row
                        DropdownMenu(
                            expanded = showWalletDropdown,
                            onDismissRequest = { showWalletDropdown = false },
                            offset = DpOffset(x = 0.dp, y = 4.dp), // Pequeño offset para posicionarlo justo debajo de la cápsula
                            modifier = Modifier
                                .widthIn(min = 200.dp)
                                .background(
                                    color = Color(0xFF252525), // Color un poco más claro que el oscuro original pero manteniendo el tono oscuro
                                    shape = RoundedCornerShape(24.dp) // Puntas más redondas
                                )
                                .clip(RoundedCornerShape(24.dp)),
                            containerColor = Color.Transparent, // Hacer el fondo del DropdownMenu transparente
                            shadowElevation = 0.dp, // Eliminar completamente la sombra
                            tonalElevation = 0.dp // Eliminar la elevación tonal
                        ) {
                            availableWallets.forEach { walletKey ->
                                val walletName = walletManager.getWalletName(walletKey) ?: "Wallet"
                                val isCurrentWallet = walletKey == publicKey
                                
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = walletName,
                                                fontWeight = if (isCurrentWallet) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrentWallet) XianPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCurrentWallet) {
                                                Text(
                                                    text = "✓",
                                                    color = XianPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (!isCurrentWallet) {
                                            walletManager.setActiveWallet(walletKey)
                                            viewModel.refreshData() // Refresh data for new wallet
                                        }
                                        showWalletDropdown = false
                                    }
                                )
                            }
                        }
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
                                Color.White
                            else 
                                Color(0xFFF44336)
                        )
                        
                        // Status indicator dot
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(8.dp)
                                .background(
                                    color = if (isNodeConnected) 
                                        Color.Green
                                    else
                                        Color(0xFFF44336),
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
    // snackbarHost removed; custom TopToastHost overlay used instead
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
            // Top overlay for toasts
            net.xian.xianwalletapp.ui.components.TopToastHost(
                state = toastHostState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
            )
            SwipeRefresh(
                state = swipeRefreshState, // Usar el estado personalizado
                onRefresh = {
                    // Vibración háptica sutil al completar el gesto
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    
                    viewModel.refreshData()
                    // Restart transaction monitoring on refresh
                    restartTransactionMonitor(context)
                },
                indicator = { _, _ -> }, // Completamente deshabilitar el indicador por defecto
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .scale(animatedScale) // Aplicar la animación de escala a todo el contenido
            ) {
                // XIAN Balance Card with neon border effect
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 220.dp) // Increased height a bit for better spacing
                        .padding(bottom = 16.dp)
                        // Multiple shadow layers for neon effect
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false,
                            ambientColor = XianPrimary.copy(alpha = 0.6f),
                            spotColor = XianPrimary.copy(alpha = 0.8f)
                        )
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false,
                            ambientColor = XianPrimaryVariant.copy(alpha = 0.4f),
                            spotColor = XianPrimaryVariant.copy(alpha = 0.6f)
                        )
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false,
                            ambientColor = XianPrimary.copy(alpha = 0.2f),
                            spotColor = XianPrimary.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(
                            width = 2.dp,
                            brush = Brush.horizontalGradient(colors = listOf(XianPrimary, XianPrimaryVariant))
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 16.dp, // Reasonable elevation
                            pressedElevation = 20.dp,
                            focusedElevation = 18.dp,
                            hoveredElevation = 18.dp
                        )
                    ) {
                    // Use Box to allow absolute positioning of the edit icon and background image
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Background image or color if selected
                        selectedCardBackground?.let { backgroundName ->
                            getBackgroundResourceId(backgroundName)?.let { resourceId ->
                                when (resourceId) {
                                    -1 -> {
                                        // Dark background
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    color = Color(0xFF1A1A1A).copy(alpha = 0.8f),
                                                    shape = RoundedCornerShape(24.dp)
                                                )
                                        )
                                    }
                                    else -> {
                                        // Image background
                                        Image(
                                            painter = painterResource(id = resourceId),
                                            contentDescription = "Card background",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(24.dp)),
                                            contentScale = ContentScale.Crop,
                                            alpha = 0.3f // Make it subtle so text remains readable
                                        )
                                    }
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                        // Add top spacing to prevent balance from being too close to the top
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Calculate total balance across all tokens
                        if (staticXianPrice == null || portfolioSnapshot == null) {
                            SmallBouncingDotsLoader(
                                modifier = Modifier.size(24.dp),
                                dotColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            val totalBalance = portfolioSnapshot?.totalUsd ?: 0f

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isBalanceVisible) "$%.2f".format(Locale.US, totalBalance) else "••••",
                                    fontSize = 65.sp, // Increased font size from 55sp to 65sp
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
                        
                        // Wallet Address Section - INSIDE CARD, BOTTOM POSITION
                        publicKey?.let { address ->
                            val truncatedAddress = if (address.length >= 10) {
                                "${address.take(5)}...${address.takeLast(5)}"
                            } else {
                                address
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(address))
                                        coroutineScope.launch {
                                            toastHostState.show("Address copied to clipboard", net.xian.xianwalletapp.ui.components.ToastType.Success)
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = truncatedAddress,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White, // Changed to white
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy address",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } // End of Column

                    // Edit icon in top-right corner
                    IconButton(
                        onClick = {
                            navController.navigate(XianDestinations.CARD_BACKGROUND_SELECTOR)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit balance card",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } // End of Box
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
                            navController.navigate(XianDestinations.SWAP)
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

                    // Farm Option
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val urlToLoad = "https://dex.xian.org/#farms"
                            val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                            navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco, // Icono de planta/eco para Farm
                            contentDescription = "Farming",
                            tint = MaterialTheme.colorScheme.onSurface // Adjusted tint for outside card
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Farming",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface // Adjusted color for outside card
                        )
                    }

                    // Analysis Option - New button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            navController.navigate(XianDestinations.PORTFOLIO_ANALYSIS)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Portfolio Analysis",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Analysis",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
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
                            else -> {
                                // Always show the list when we have tokens, regardless of isLoading state
                                // State variables for scroll tracking in tokens list
                                var lastTokensScrollIndex by remember { mutableStateOf(0) }
                                var lastTokensScrollOffset by remember { mutableStateOf(0) }
                                val tokensListState = rememberLazyListState()
                                
                                // Keep bottom bar always visible on Tokens tab; only track indices
                                LaunchedEffect(tokensListState.firstVisibleItemIndex, tokensListState.firstVisibleItemScrollOffset) {
                                    val index = tokensListState.firstVisibleItemIndex
                                    val offset = tokensListState.firstVisibleItemScrollOffset
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
                                            xwtPrice = if (contract == "con_xwt") xwtPrice else null, // Pasar el precio de XWT
                                            slitherPrice = if (contract == "con_slither") slitherPrice else null, // Pass SLITHER price
                                            imageLoader = viewModel.getImageLoader(), // Pass the custom image loader
                                            balanceVisible = isBalanceVisible, // Pass balance visibility state
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
                            // Keep bottom bar always visible on Items tab; only track indices
                            LaunchedEffect(collectiblesGridState.firstVisibleItemIndex, collectiblesGridState.firstVisibleItemScrollOffset) {
                                val index = collectiblesGridState.firstVisibleItemIndex
                                val offset = collectiblesGridState.firstVisibleItemScrollOffset
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
                                        imageLoader = viewModel.getNftImageLoader(),
                                        onViewClick = { url: String? -> // Add explicit type for url
                                            url?.let { urlString: String -> // Explicitly type 'urlString'
                                                try {
                                                    // Encode the URL before navigating
                                                    val encodedUrl = URLEncoder.encode(urlString, StandardCharsets.UTF_8.toString())
                                                    // Navigate to the in-app browser screen
                                                    navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                                                } catch (e: Exception) {
                                                    coroutineScope.launch {
                                                        toastHostState.show("Could not open URL: Invalid format", net.xian.xianwalletapp.ui.components.ToastType.Error)
                                                        Log.e("WalletScreen", "Error encoding or navigating to URL: $urlString", e)
                                                    }
                                                }
                                            } ?: run {
                                                // Handle case where URL is null, if necessary
                                                coroutineScope.launch {
                                                    toastHostState.show("Cannot open: URL is missing", net.xian.xianwalletapp.ui.components.ToastType.Error)
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
                        // Keep bottom bar always visible on Activity tab; only track indices
                        LaunchedEffect(activityListState.firstVisibleItemIndex, activityListState.firstVisibleItemScrollOffset) {
                            val index = activityListState.firstVisibleItemIndex
                            val offset = activityListState.firstVisibleItemScrollOffset
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
                                                    token.contract == "con_slither" -> R.drawable.sss
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
    xwtPrice: Float? = null, // Añadir precio de XWT en XIAN
    slitherPrice: Float? = null, // Add SLITHER price parameter
    imageLoader: ImageLoader, // Add ImageLoader parameter
    balanceVisible: Boolean, // Add balance visibility parameter
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
            balanceVisible = balanceVisible, // Pass balance visibility
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
                "con_xwt" -> xwtPrice
                "con_slither" -> slitherPrice
                else -> null
            },
            imageLoader = imageLoader, // Pass down the loader
            balanceVisible = balanceVisible, // Pass balance visibility
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
    balanceVisible: Boolean, // Add balance visibility parameter
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
                            text = if (balanceVisible) "%.1f".format(Locale.US, balance) else "••••",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (xianPrice != null) {
                            // Mostrar el precio en USD para XIAN con formato "$"
                            Text(
                                text = if (balanceVisible) "$%.6f".format(Locale.US, xianPrice) else "••••",
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
    balanceVisible: Boolean, // Add balance visibility parameter
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
                            contract == "con_xwt" -> R.drawable.xwtlogo
                            contract == "con_slither" -> R.drawable.sss
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
                            text = if (balanceVisible) "%.1f".format(Locale.US, balance) else "••••",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (usdValue != null) {
                            Text(
                                text = if (balanceVisible) "$%.2f".format(Locale.US, usdValue) else "••••",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else if (xianPrice != null) {
                            // Mostrar el precio en XIAN para POOP y XTFU con formato "X*"
                            Text(
                                text = if (balanceVisible) "X*%.6f".format(Locale.US, xianPrice) else "••••",
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
