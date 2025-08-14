package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
// Imports para Custom Canvas Chart
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.navigation.XianNavArgs
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModelFactory
import net.xian.xianwalletapp.wallet.WalletManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import net.xian.xianwalletapp.data.LocalTransactionRecord
import net.xian.xianwalletapp.ui.components.TransactionRecordItem
import net.xian.xianwalletapp.ui.components.LargeBouncingDotsLoader

/**
 * Token detail screen showing price card, balance, and action buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(
    navController: NavController,
    walletManager: WalletManager,
    networkService: XianNetworkService,
    tokenContract: String,
    tokenSymbol: String,
    viewModel: WalletViewModel
) {
    val context = LocalContext.current      // Collect states from ViewModel
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    val poopPrice by viewModel.poopPrice.collectAsStateWithLifecycle()
    val xtfuPrice by viewModel.xtfuPrice.collectAsStateWithLifecycle()
    val xarbPrice by viewModel.xarbPrice.collectAsStateWithLifecycle()
    val xwtPrice by viewModel.xwtPrice.collectAsStateWithLifecycle()
    val isChartLoading by viewModel.isChartLoading.collectAsStateWithLifecycle()
    val chartError by viewModel.chartError.collectAsStateWithLifecycle()
    val chartNormalizationType by viewModel.chartNormalizationType.collectAsStateWithLifecycle()
    val chartYAxisRange by viewModel.chartYAxisRange.collectAsStateWithLifecycle()
    val chartYAxisOffset by viewModel.chartYAxisOffset.collectAsStateWithLifecycle()
      // Estado para controlar si la tarjeta de precio está expandida
    var isPriceCardExpanded by remember { mutableStateOf(false) }
    
    // State for holders count
    var holdersCount by remember { mutableStateOf<Int?>(null) }
    var isLoadingHolders by remember { mutableStateOf(false) }
      // State for total supply
    var totalSupply by remember { mutableStateOf<String?>(null) }
    var isLoadingTotalSupply by remember { mutableStateOf(false) }
    
    // State for 24h price change
    var priceChange24h by remember { mutableStateOf<Float?>(null) }
    var isLoadingPriceChange by remember { mutableStateOf(false) }
    
    // State for time period selection
    var selectedTimePeriod by remember { mutableStateOf("1M") }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Get token information
    val tokenInfo = tokenInfoMap[tokenContract]
    val balance = balanceMap[tokenContract] ?: 0f
    val tokenName = tokenInfo?.name ?: tokenContract
    val logoUrl = tokenInfo?.logoUrl
      // Calculate USD/XIAN value based on token type
    val tokenPrice = when (tokenContract) {
        "currency" -> xianPrice
        "con_poop_coin" -> poopPrice
        "con_xtfu" -> xtfuPrice
        "con_xarb" -> xarbPrice
        "con_xwt" -> xwtPrice
        else -> null
    }    // Create formatters for different values
    val usdFormatter = DecimalFormat("#,##0.0000") // For USD values (4 decimals)
    val priceFormatter = DecimalFormat("#,##0.000000") // For token prices in XIAN (6 decimals)
    val balanceFormatter = DecimalFormat("#,##0.####")
    val percentageFormatter = DecimalFormat("#,##0.00") // For percentage values (2 decimals)

    // Obtener el ChartModelProducer del ViewModel
    val chartModelProducer = viewModel.chartModelProducer
    
    // Obtener datos del gráfico del ViewModel
    val priceData by viewModel.chartData.collectAsStateWithLifecycle()
    
    // Debug logging
    LaunchedEffect(priceData) {
        android.util.Log.d("TokenDetailScreen", "Chart data updated: ${priceData.size} points")
        if (priceData.isNotEmpty()) {
            android.util.Log.d("TokenDetailScreen", "First 5 values: ${priceData.take(5)}")
        }
    }
    
    // Cargar datos históricos cuando el tokenContract o el período cambien
    LaunchedEffect(tokenContract, selectedTimePeriod) {
        viewModel.loadHistoricalData(tokenContract, selectedTimePeriod)
    }
      // Load holders count when tokenContract changes
    LaunchedEffect(tokenContract) {
        isLoadingHolders = true
        coroutineScope.launch {
            try {
                holdersCount = networkService.getTokenHolders(tokenContract)
            } catch (e: Exception) {
                android.util.Log.e("TokenDetailScreen", "Error loading holders: ${e.message}")
                holdersCount = null
            } finally {
                isLoadingHolders = false
            }
        }
    }
      // Load total supply when tokenContract changes
    LaunchedEffect(tokenContract) {
        isLoadingTotalSupply = true
        coroutineScope.launch {
            try {
                totalSupply = networkService.getTokenTotalSupply(tokenContract)
            } catch (e: Exception) {
                android.util.Log.e("TokenDetailScreen", "Error loading total supply: ${e.message}")
                totalSupply = null
            } finally {
                isLoadingTotalSupply = false
            }
        }
    }
    
    // Load 24h price change when tokenContract changes
    LaunchedEffect(tokenContract) {
        isLoadingPriceChange = true
        coroutineScope.launch {
            try {
                // First, find the trading pair for this token
                val allPairs = networkService.getAllPairs()
                android.util.Log.d("TokenDetailScreen", "Found ${allPairs.size} pairs, looking for token: $tokenContract")
                
                val tokenPair = if (tokenContract == "currency") {
                    // For XIAN currency, look for XIAN/USDC pair
                    allPairs.find { pair ->
                        (pair.token0 == "currency" && pair.token1 == "con_usdc") ||
                        (pair.token1 == "currency" && pair.token0 == "con_usdc")
                    }
                } else {
                    // For other tokens, look for token/XIAN pair
                    allPairs.find { pair ->
                        (pair.token0 == tokenContract && pair.token1 == "currency") ||
                        (pair.token1 == tokenContract && pair.token0 == "currency")
                    }
                }
                
                if (tokenPair != null) {
                    android.util.Log.d("TokenDetailScreen", "Found pair for $tokenContract: ${tokenPair.id} (${tokenPair.token0}/${tokenPair.token1})")
                    
                    // Determine which token denomination to use:
                    val tokenDenomination = if (tokenContract == "currency") {
                        // For XIAN, we want USD per XIAN (USDC per XIAN)
                        if (tokenPair.token0 == "currency") {
                            1 // token1 (USDC) per token0 (XIAN)
                        } else {
                            0 // token0 (USDC) per token1 (XIAN)
                        }
                    } else {
                        // For other tokens, we want XIAN per token
                        if (tokenPair.token0 == tokenContract) {
                            1 // token1 (XIAN) per token0 (our token)
                        } else {
                            0 // token0 (XIAN) per token1 (our token)
                        }
                    }
                    
                    android.util.Log.d("TokenDetailScreen", "Using denomination $tokenDenomination for pair ${tokenPair.id}")
                    
                    val result = networkService.getPriceChange24h(tokenPair.id, tokenDenomination)
                    priceChange24h = if (result != null && result.isFinite()) {
                        android.util.Log.d("TokenDetailScreen", "Successfully loaded 24h price change: $result% for $tokenContract")
                        result
                    } else {
                        android.util.Log.w("TokenDetailScreen", "Invalid price change result: $result for $tokenContract")
                        null
                    }
                } else {
                    val pairType = if (tokenContract == "currency") "XIAN/USDC" else "$tokenContract/XIAN"
                    android.util.Log.w("TokenDetailScreen", "No trading pair found for $pairType")
                    priceChange24h = null
                }
            } catch (e: Exception) {
                android.util.Log.e("TokenDetailScreen", "Error loading 24h price change for $tokenContract: ${e.message}", e)
                priceChange24h = null
            } finally {
                isLoadingPriceChange = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Text(
                        text = tokenName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {// Expandible Price Card with Chart
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable { isPriceCardExpanded = !isPriceCardExpanded },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(colors = listOf(XianPrimary, XianPrimaryVariant))
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Cabecera con precio y flecha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Token Price",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                              if (tokenPrice != null) {
                                Text(
                                    text = if (tokenContract == "currency") {
                                        "$${usdFormatter.format(tokenPrice)} USD"
                                    } else {
                                        "${priceFormatter.format(tokenPrice)} XIAN"
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // Show 24h price change
                                if (isLoadingPriceChange) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Loading 24h change...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                } else if (priceChange24h != null && priceChange24h!!.isFinite()) {
                                    val isPositive = priceChange24h!! >= 0
                                    val changeColor = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    val changeText = if (isPositive) "+${percentageFormatter.format(priceChange24h)}%" else "${percentageFormatter.format(priceChange24h)}%"
                                    val changeIcon = if (isPositive) "▲" else "▼"
                                    
                                    Surface(
                                        modifier = Modifier.padding(top = 8.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = changeColor.copy(alpha = 0.1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = changeIcon,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = changeColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = changeText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = changeColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "24h",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else if (tokenContract != "currency") {
                                    // Show "No data" message for non-XIAN tokens when price change is not available
                                    Surface(
                                        modifier = Modifier.padding(top = 8.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "—",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "24h data unavailable",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Price not available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        // Icono de flecha indicando expansión
                        Icon(
                            imageVector = if (isPriceCardExpanded) 
                                Icons.Default.KeyboardArrowUp 
                            else 
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isPriceCardExpanded) "Collapse chart" else "Expand chart",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Gráfico expandible con animación
                    AnimatedVisibility(
                        visible = isPriceCardExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$tokenSymbol Price Chart",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                              // Indicador de escala mejorada si está activa
                            if (chartNormalizationType != null) {
                                Text(
                                    text = chartNormalizationType!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = XianPrimary.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            
                            // Time Period Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val timePeriods = listOf("1D", "1W", "1M", "1Y")
                                timePeriods.forEach { period ->
                                    FilterChip(
                                        onClick = {
                                            selectedTimePeriod = period
                                        },
                                        label = {
                                            Text(
                                                text = period,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        selected = selectedTimePeriod == period,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = XianPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }

                            // Contenedor del gráfico con altura fija
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            ) {
                                if (isChartLoading) {
                                    // Indicador de carga
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Loading price data...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                } else if (chartError != null) {
                                    // Error en el gráfico
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Chart error",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = chartError!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )                                        }
                                    }                                } else if ((priceData as List<Float>).isNotEmpty()) {
                                    
                                    // Vico Chart con ejes personalizados y escala mejorada para pequeños cambios
                                    SimpleCryptoChart(
                                        data = priceData,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // Sin datos
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No price data available",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }            }
            
            // Token Balance and Logo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Balance information
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Balance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "${balanceFormatter.format(balance)} $tokenSymbol",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                          // Show USD/XIAN equivalent if price is available
                        if (tokenPrice != null && balance > 0) {
                            val totalValue = balance * tokenPrice
                            Text(
                                text = if (tokenContract == "currency") {
                                    "≈ $${usdFormatter.format(totalValue)} USD"
                                } else {
                                    "≈ ${priceFormatter.format(totalValue)} XIAN"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Token logo
                    // Use Image composable for local resource, AsyncImage for URL
                    if (tokenContract == "currency") {
                        Image(
                            painter = painterResource(id = R.drawable.xian_logo), // Use local resource directly
                            contentDescription = "$tokenName Logo",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Inside
                        )                    } else {
                        AsyncImage(
                            model = when {
                                tokenContract == "con_xarb" -> "file:///android_asset/xarb.jpg"
                                else -> logoUrl
                            },
                            contentDescription = "$tokenName Logo",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Inside,
                            error = painterResource(id = R.drawable.ic_question_mark),
                            placeholder = painterResource(id = R.drawable.ic_question_mark)
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Send Button
                Button(
                    onClick = {
                        navController.navigate(
                            "${XianDestinations.SEND_TOKEN}?${XianNavArgs.TOKEN_CONTRACT}=$tokenContract&${XianNavArgs.TOKEN_SYMBOL}=$tokenSymbol"
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    colors = xianButtonColors(XianButtonType.PRIMARY),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = "Send",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Receive Button
                Button(
                    onClick = {
                        navController.navigate(XianDestinations.RECEIVE_TOKEN)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    colors = xianButtonColors(XianButtonType.SECONDARY),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Receive",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Receive",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Swap Button (was Exchange)
                Button(
                    onClick = {
                        when (tokenContract) {
                            "currency" -> {
                                // For XIAN, navigate to default swap (XIAN/USDC)
                                android.util.Log.d("TokenDetailScreen", "Navigation - XIAN token, using default swap")
                                navController.navigate(XianDestinations.SWAP)
                            }
                            else -> {
                                // For other tokens, specify the pair
                                val fromToken = tokenContract
                                val toToken = when (tokenContract) {
                                    "con_usdc" -> "currency" // USDC -> XIAN
                                    else -> "currency"       // Other tokens -> XIAN
                                }
                                android.util.Log.d("TokenDetailScreen", "Navigation - fromToken: $fromToken, toToken: $toToken, tokenContract: $tokenContract")
                                navController.navigate("${XianDestinations.SWAP}?fromToken=$fromToken&toToken=$toToken")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    colors = xianButtonColors(XianButtonType.PRIMARY),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Swap",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Swap", // Cambiado de "Exchange" a "Swap"
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Expandable Token Information Section
            ExpandableTokenInformationSection(
                tokenContract = tokenContract,
                tokenSymbol = tokenSymbol,
                holdersCount = holdersCount,
                isLoadingHolders = isLoadingHolders,
                totalSupply = totalSupply,
                isLoadingTotalSupply = isLoadingTotalSupply
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Token Activity Section (expandable like Token Information)
            ExpandableTokenActivitySection(
                viewModel = viewModel,
                tokenContract = tokenContract,
                tokenSymbol = tokenSymbol,
                navController = navController
            )
        }
    }
}

/**
 * Simple static crypto chart implementation using Canvas
 * This chart displays all data points within the container without scrolling
 */
@Composable
fun SimpleCryptoChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 24.dp.toPx()
        
        val chartWidth = width - (padding * 2)
        val chartHeight = height - (padding * 2)
        
        if (chartWidth <= 0 || chartHeight <= 0 || data.size < 2) return@Canvas
        
        // Reverse data so most recent appears on the right
        val reversedData = data.reversed()
        
        // Calculate min and max values for scaling
        val minValue = reversedData.minOrNull() ?: 0f
        val maxValue = reversedData.maxOrNull() ?: 1f
        val valueRange = maxValue - minValue
        
        // If all values are the same, create a small range for display
        val displayRange = if (valueRange == 0f) maxValue * 0.01f else valueRange
        val displayMin = if (valueRange == 0f) minValue - (maxValue * 0.005f) else minValue
        val displayMax = if (valueRange == 0f) maxValue + (maxValue * 0.005f) else maxValue
        
        // Create path for the line chart
        val path = Path()
        val stepX = chartWidth / (reversedData.size - 1)
        
        // Calculate first point
        val startX = padding
        val startY = padding + chartHeight - ((reversedData[0] - displayMin) / displayRange * chartHeight)
        path.moveTo(startX, startY)
        
        // Add subsequent points
        for (i in 1 until reversedData.size) {
            val x = padding + (i * stepX)
            val y = padding + chartHeight - ((reversedData[i] - displayMin) / displayRange * chartHeight)
            path.lineTo(x, y)
        }
        
        // Draw the line with primary color
        drawPath(
            path = path,
            color = XianPrimary,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}
/**
 * Expandable Token Activity Section showing token-specific transaction history
 */
@Composable
fun ExpandableTokenActivitySection(
    viewModel: WalletViewModel,
    tokenContract: String,
    tokenSymbol: String,
    navController: NavController
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Collect token transaction states from ViewModel
    val tokenTransactionHistory by viewModel.tokenTransactionHistory.collectAsStateWithLifecycle()
    val isTokenTransactionHistoryLoading by viewModel.isTokenTransactionHistoryLoading.collectAsStateWithLifecycle()
    val tokenTransactionHistoryError by viewModel.tokenTransactionHistoryError.collectAsStateWithLifecycle()

    // Load token transaction history when the component is first composed or token changes
    LaunchedEffect(tokenContract) {
        viewModel.loadTokenTransactionHistory(tokenContract, force = true)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header - Always visible, with same color as activity cards (surface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$tokenSymbol Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    when {
                        isTokenTransactionHistoryLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LargeBouncingDotsLoader()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Loading $tokenSymbol transactions...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        tokenTransactionHistoryError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Error: $tokenTransactionHistoryError",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.loadTokenTransactionHistory(tokenContract, force = true)
                                    }
                                ) {
                                    Text("Retry")
                                }
                            }
                        }

                        tokenTransactionHistory.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Newspaper,
                                        contentDescription = "No transactions",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No $tokenSymbol transactions found",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        else -> {
                            // Filter out consecutive duplicates and group by date
                            val distinctTransactionHistory = tokenTransactionHistory.fold(mutableListOf<LocalTransactionRecord>()) { acc, record ->
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

                            // Show limited number of transactions (e.g., last 15)
                            val maxTransactionsToShow = 15
                            var transactionCount = 0

                            LazyColumn(
                                modifier = Modifier.heightIn(max = 400.dp)
                            ) {
                                groupedTransactions.forEach { (date, records) ->
                                    if (transactionCount < maxTransactionsToShow) {
                                        item {
                                            // Date header
                                            Text(
                                                text = date.format(
                                                    java.time.format.DateTimeFormatter
                                                        .ofPattern("MMMM d")
                                                        .withLocale(java.util.Locale.ENGLISH)
                                                ),
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        val recordsToShow = records.take(maxTransactionsToShow - transactionCount)
                                        itemsIndexed(recordsToShow) { index, record ->
                                            val isFirst = index == 0
                                            val isLast = index == recordsToShow.size - 1
                                            TransactionRecordItem(
                                                record = record,
                                                navController = navController,
                                                dense = true,
                                                topRounded = isFirst,
                                                bottomRounded = isLast
                                            )

                                            transactionCount++
                                        }
                                    }
                                }

                                // Show "View All" option if there are more transactions
                                if (distinctTransactionHistory.size > maxTransactionsToShow) {
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(
                                            onClick = {
                                                // Navigate to full activity view or expand the list
                                                // For now, we'll just reload to show more
                                                viewModel.loadTokenTransactionHistory(tokenContract, force = true)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "View All ${distinctTransactionHistory.size} Transactions",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
/**

 * Expandable Token Information Section
 */
@Composable
fun ExpandableTokenInformationSection(
    tokenContract: String,
    tokenSymbol: String,
    holdersCount: Int?,
    isLoadingHolders: Boolean,
    totalSupply: String?,
    isLoadingTotalSupply: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header - Always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Token Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.Default.KeyboardArrowUp 
                    else 
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    // Add a subtle divider
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    
                    // Contract address
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Contract:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (tokenContract == "currency") "Native XIAN" else tokenContract,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Symbol
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Symbol:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tokenSymbol,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Holders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Holders:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (isLoadingHolders) {
                            Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                text = holdersCount?.let { "$it" } ?: "N/A",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Total Supply
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Supply:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (isLoadingTotalSupply) {
                            Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                text = totalSupply ?: "N/A",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}