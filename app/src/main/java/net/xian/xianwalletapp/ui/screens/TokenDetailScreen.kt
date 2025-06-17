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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
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
// Imports para Vico Chart
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.component.text.textComponent
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
    viewModel: WalletViewModel = viewModel(
        factory = WalletViewModelFactory(LocalContext.current, walletManager, networkService)
    )
) {
    val context = LocalContext.current      // Collect states from ViewModel
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    val poopPrice by viewModel.poopPrice.collectAsStateWithLifecycle()
    val xtfuPrice by viewModel.xtfuPrice.collectAsStateWithLifecycle()
    val xarbPrice by viewModel.xarbPrice.collectAsStateWithLifecycle()
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
        else -> null
    }    // Create formatters for different values
    val usdFormatter = DecimalFormat("#,##0.0000") // For USD values (4 decimals)
    val priceFormatter = DecimalFormat("#,##0.000000") // For token prices in XIAN (6 decimals)
    val balanceFormatter = DecimalFormat("#,##0.####")
    val percentageFormatter = DecimalFormat("#,##0.00") // For percentage values (2 decimals)

    // Obtener el ChartModelProducer del ViewModel
    val chartModelProducer = viewModel.chartModelProducer    // Cargar datos históricos cuando el tokenContract cambie
    LaunchedEffect(tokenContract) {
        viewModel.loadHistoricalData(tokenContract)
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
                val tokenPair = allPairs.find { pair ->
                    pair.token0 == tokenContract || pair.token1 == tokenContract
                }
                
                if (tokenPair != null) {
                    // Determine which token denomination to use:
                    // 0 = token0-per-token1 (default), 1 = token1-per-token0
                    val tokenDenomination = when {
                        tokenPair.token0 == tokenContract && tokenPair.token1 == "currency" -> 1 // We want XIAN per token
                        tokenPair.token1 == tokenContract && tokenPair.token0 == "currency" -> 0 // We want XIAN per token
                        tokenPair.token0 == tokenContract -> 1 // token1 per token0
                        tokenPair.token1 == tokenContract -> 0 // token0 per token1
                        else -> 0 // default
                    }
                    
                    val result = networkService.getPriceChange24h(tokenPair.id, tokenDenomination)
                    priceChange24h = if (result != null && result.isFinite()) result else null
                } else {
                    android.util.Log.w("TokenDetailScreen", "No trading pair found for token $tokenContract")
                    priceChange24h = null
                }
            } catch (e: Exception) {
                android.util.Log.e("TokenDetailScreen", "Error loading 24h price change: ${e.message}")
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
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "Loading 24h change...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }                                } else if (priceChange24h != null && priceChange24h!!.isFinite()) {
                                    val isPositive = priceChange24h!! >= 0
                                    val changeColor = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    val changeText = if (isPositive) "+${percentageFormatter.format(priceChange24h)}%" else "${percentageFormatter.format(priceChange24h)}%"
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = changeText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = changeColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "(24h)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
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
                                    }                                } else if (chartModelProducer.getModel()?.entries?.isNotEmpty() == true) {
                                    
                                    // Vico Chart con ejes personalizados y escala mejorada para pequeños cambios
                                    Chart(
                                        chart = lineChart(),
                                        chartModelProducer = chartModelProducer,
                                        startAxis = startAxis(
                                            label = textComponent {
                                                color = Color.White.toArgb()
                                                textSizeSp = 10f
                                            },                                            // Configurar para mostrar precios reales con mejor precisión
                                            valueFormatter = { value, _ ->
                                                // Apply offset back to get real price if offset is used
                                                val realValue = chartYAxisOffset?.let { offset -> value + offset } ?: value
                                                when {
                                                    realValue >= 1000 -> {
                                                        // Para valores grandes
                                                        String.format("%.0f", realValue)
                                                    }
                                                    realValue >= 100 -> {
                                                        // Para valores medianos
                                                        String.format("%.1f", realValue)
                                                    }
                                                    realValue >= 1 -> {
                                                        // Para valores normales
                                                        String.format("%.3f", realValue)
                                                    }
                                                    realValue >= 0.001 -> {
                                                        // Para valores pequeños (típico de tokens)
                                                        String.format("%.6f", realValue)
                                                    }                                                    else -> {
                                                        // Para valores muy pequeños
                                                        String.format("%.8f", realValue)
                                                    }
                                                }
                                            }
                                        ),
                                        bottomAxis = bottomAxis(
                                            label = textComponent {
                                                color = Color.White.toArgb()
                                                textSizeSp = 10f
                                            },                                            // Formatear el eje X para mostrar tiempo más intuitivo
                                            valueFormatter = { value, _ ->
                                                val index = value.toInt()
                                                when {
                                                    index % 12 == 0 -> "${index * 15 / 60}h" // Cada 3 horas
                                                    index % 4 == 0 -> "${index * 15}m" // Cada hora
                                                    else -> ""
                                                }
                                            }
                                        ),
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
                            Icons.Default.Send,
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
                        // Abrir Swap igual que en WalletScreen
                        val urlToLoad = "https://snakexchange.org/"
                        val encodedUrl = URLEncoder.encode(urlToLoad, StandardCharsets.UTF_8.toString())
                        navController.navigate("${XianDestinations.WEB_BROWSER}?url=$encodedUrl")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
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
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Additional token information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Token Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
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
                        )                    }
                    
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
