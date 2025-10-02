package net.xian.xianwalletapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import net.xian.xianwalletapp.ui.components.XianBottomNavBar
import net.xian.xianwalletapp.ui.viewmodels.NavigationViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.min
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.network.OpenRouterService
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.log10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioAnalysisScreen(
    navController: NavController,
    navigationViewModel: NavigationViewModel,
    walletViewModel: net.xian.xianwalletapp.ui.viewmodels.WalletViewModel
) {
    // Sync navigation state when entering screen
    LaunchedEffect(Unit) {
        // Since this is accessed from the wallet screen, we don't update navigation here
        // The bottom navigation remains in "Portfolio" state
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Portfolio Analysis",
                            tint = XianPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Portfolio Analysis",
                            color = XianPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            XianBottomNavBar(
                navController = navController,
                navigationViewModel = navigationViewModel
            )
        }
    ) { paddingValues ->
// --- Access Gate: Require >= 4.00 USDC equivalent in XWT to view this screen ---
val xianUsd by walletViewModel.xianPrice.collectAsState()
val xwtPxInXian by walletViewModel.xwtPrice.collectAsState()
val balanceMap by walletViewModel.balanceMap.collectAsState()

val thresholdUsdc = 4.0f
val xwtBalance = balanceMap["con_xwt"] ?: 0f

// Reuse same conversion chain as AI fee: USDC -> XIAN -> XWT (and inverse for valuation)
// haveUsdc = XWT_balance * (XWT price in XIAN) * (XIAN price in USDC)
val haveUsdc = xianUsd?.let { xu ->
    xwtPxInXian?.let { px ->
        xwtBalance * px * xu
    }
}
// requiredXwt = (USDC_needed / XIAN_USD) / XWT_in_XIAN
val requiredXwt = xianUsd?.let { xu ->
    xwtPxInXian?.let { px ->
        if (xu > 0f && px > 0f) (thresholdUsdc / xu) / px else null
    }
}

if (haveUsdc == null || requiredXwt == null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Checking access...", style = MaterialTheme.typography.bodyMedium)
        }
    }
    return@Scaffold
}

if (haveUsdc < thresholdUsdc) {
    val needUsdc = (thresholdUsdc - haveUsdc).coerceAtLeast(0f)
    val needXwt = ((needUsdc / (xianUsd ?: 1f)) / (xwtPxInXian ?: 1f)).coerceAtLeast(0f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Access restricted",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "To view Portfolio Analysis you need at least ${'$'}4.00 USDC equivalent in XWT.\n" +
                           "You currently have ${'$'}${String.format(java.util.Locale.US, "%.2f", haveUsdc)}.\n" +
                           "Shortfall: ${'$'}${String.format(java.util.Locale.US, "%.2f", needUsdc)} (~${String.format(java.util.Locale.US, "%.6f", needXwt)} XWT).",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { navController.popBackStack() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.Black)
                    ) { Text("Go back") }
                }
            }
        }
    }
    return@Scaffold
}
// --- End Access Gate ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Portfolio Summary Card
            val snapshotState by walletViewModel.portfolioSnapshot.collectAsState()
            val snapshot = snapshotState // local val for smart-cast friendliness
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Total Portfolio Value",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalText = snapshot?.let { "${'$'}" + String.format("%.2f", it.totalUsd) } ?: "Loading..."
                    Text(
                        text = totalText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = XianPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (snapshot == null) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching snapshot...", fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Snapshot",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            snapshot?.let { snap ->
                                Text(
                                    text = "Snapshot @ " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(snap.timestamp)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Token Distribution Pie Chart
            Text(
                text = "Token Distribution",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val entries = snapshot?.tokens?.filter { it.usdValue > 0f } ?: emptyList()
                    if (entries.isEmpty()) {
                        Box(modifier = Modifier.height(160.dp), contentAlignment = Alignment.Center) {
                            Text(if (snapshot == null) "Loading distribution..." else "No token value", fontSize = 14.sp)
                        }
                    } else {
                        var highlighted by remember { mutableStateOf<String?>(null) }
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LivePieChart(
                                data = entries,
                                highlighted = highlighted,
                                onSliceClick = { contract -> highlighted = if (highlighted == contract) null else contract }
                            )
                        }
                        if (entries.isNotEmpty()) {
                            val h = highlighted?.let { c -> entries.find { it.contract == c } }
                            if (h != null) {
                                Text(
                                    text = "${h.symbol ?: h.contract}: ${String.format("%.2f", h.usdValue)} USD (${String.format("%.1f", h.percent)}%)",
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Legend
                    if (snapshot != null && snapshot.tokens.isNotEmpty()) {
                        Column {
                            snapshot.tokens
                                .filter { it.usdValue > 0f }
                                .sortedByDescending { it.usdValue }
                                .forEach { t ->
                                    LegendItem(
                                        color = sliceColorFor(t.contract),
                                        label = t.symbol ?: t.contract,
                                        percentage = String.format("%.1f%%", t.percent),
                                        value = "${'$'}" + String.format("%.2f", t.usdValue)
                                    )
                                }
                        }
                    }
                }
            }

            // Balance History Chart
            val perf by walletViewModel.portfolio7dPerformance.collectAsState()
            val usedFallback by walletViewModel.portfolioPerfUsedFallback.collectAsState()
            val contributions by walletViewModel.tokenContributions.collectAsState()
            var showContribSheet by remember { mutableStateOf(false) }

            Text(
                text = "Backtested Performance (Current Holdings)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clickable { if (contributions.isNotEmpty()) showContribSheet = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "7 Day Performance",
                            style = MaterialTheme.typography.titleMedium
                        )
                        // Metric: final percentage change over 7-day window
                        val finalChange = perf.lastOrNull()
                        if (finalChange != null) {
                            val isPositive = finalChange >= 0f
                            val color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (isPositive) "+" else "") + String.format("%.2f%%", finalChange),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                                if (usedFallback) {
                                    Spacer(Modifier.width(8.dp))
                                    // Subtle fallback indicator
                                    Text(
                                        text = "approx",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sparkline (168 hourly points, oldest -> newest)
                    LineChart(
                        data = perf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap for per-token contributions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (showContribSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showContribSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Per-Token Contributions (7D)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (contributions.isEmpty()) {
                            Text(
                                text = "No contribution data available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            contributions.forEach { c ->
                                val signColor = if (c.finalContributionPercent >= 0f) Color(0xFF4CAF50) else Color(0xFFF44336)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(sliceColorFor(c.contract))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = c.symbol ?: c.contract,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Weight: " + String.format("%.2f%%", c.weightPercent),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        // Show raw token 7D change for clarity so user sees sign independent of contribution
                                        Text(
                                            text = "7D Change: " + (if (c.token7dChangePercent >= 0f) "+" else "") + String.format("%.2f%%", c.token7dChangePercent),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "Contribution: " + (if (c.finalContributionPercent >= 0f) "+" else "") + String.format("%.2f%%", c.finalContributionPercent),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = signColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Button(
                            onClick = { showContribSheet = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Token Popularity vs XIAN
            Text(
                text = "Token Popularity vs XIAN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
            )

            var showPopularityCounts by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable { showPopularityCounts = !showPopularityCounts },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Popularity (% of XIAN holders)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TokenPopularitySection(
                        snapshotTokens = snapshot?.tokens,
                        showCounts = showPopularityCounts
                    )
                }
            }

            // Market Sentiment Card - shows 24h volume and 24h price change using XIAN API
            run {
                val context = LocalContext.current
                val networkService = remember { XianNetworkService.getInstance(context) }
 
                var refreshKey by remember { mutableStateOf(0) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
 
                var volume by remember { mutableStateOf<Double?>(null) }
                var changePct by remember { mutableStateOf<Double?>(null) }
                var priceNow by remember { mutableStateOf<Double?>(null) }
                var price24hAgo by remember { mutableStateOf<Double?>(null) }
 
                // Last 50 trades aggregation (USDC side)
                var buyCount by remember { mutableStateOf(0) }
                var sellCount by remember { mutableStateOf(0) }
                var buySumUsdc by remember { mutableStateOf(0.0) }
                var sellSumUsdc by remember { mutableStateOf(0.0) }

                // Market Sentiment score (0..100) and needle angle (0..180 degrees)
                var sentimentScore by remember { mutableStateOf<Double?>(null) }
                var needleTarget by remember { mutableStateOf(0f) }

                LaunchedEffect(refreshKey) {
                    isLoading = true
                    error = null
                    try {
                        // Fetch 24h volume
                        val volResp = networkService.apiService.getPairVolume24h(pairId = "1", token = "0", ts = System.currentTimeMillis())
                        if (volResp.isSuccessful) {
                            volume = volResp.body()?.volume24h
                        } else {
                            error = "HTTP ${volResp.code()} - ${volResp.message()}"
                        }
 
                        // Fetch 24h price change
                        val chgResp = networkService.apiService.getPairPriceChange24h(pairId = "1", token = "0", ts = System.currentTimeMillis())
                        if (chgResp.isSuccessful) {
                            val body = chgResp.body()
                            changePct = body?.changePct
                            priceNow = body?.priceNow
                            price24hAgo = body?.price24hAgo
                        } else {
                            // Only override error if not already set by previous call
                            if (error == null) {
                                error = "HTTP ${chgResp.code()} - ${chgResp.message()}"
                            }
                        }
 
                        // Fetch last 50 trades and aggregate
                        val tradesResp = networkService.apiService.getPairTrades(pairId = "1", token = "0", offset = 0, limit = 50, ts = System.currentTimeMillis())
                        if (tradesResp.isSuccessful) {
                            val trades = tradesResp.body()?.trades.orEmpty()
                            var bCount = 0
                            var sCount = 0
                            var bSum = 0.0
                            var sSum = 0.0
                            trades.forEach { t ->
                                when (t.side.lowercase()) {
                                    "buy" -> {
                                        bCount += 1
                                        bSum += t.amount1
                                    }
                                    "sell" -> {
                                        sCount += 1
                                        sSum += t.amount1
                                    }
                                }
                            }
                            buyCount = bCount
                            sellCount = sCount
                            buySumUsdc = bSum
                            sellSumUsdc = sSum

                            // Compute market sentiment score (0..100) and target needle angle (0..180)
                            val volVal = volume ?: 0.0
                            val volNorm = if (volVal > 0) ((log10(volVal).coerceAtLeast(0.0) / 6.0) * 100.0).coerceIn(0.0, 100.0) else 0.0

                            val priceVal = changePct ?: 0.0 // e.g., -0.9 means -0.9%
                            val priceNorm = (50.0 + (priceVal * 5.0)).coerceIn(0.0, 100.0)

                            val buys = buyCount
                            val sells = sellCount
                            val ratio = when {
                                sells <= 0 && buys > 0 -> 2.0 // cap to 100 after *50
                                sells <= 0 && buys == 0 -> 1.0 // neutral if no trades
                                else -> buys.toDouble() / sells.toDouble()
                            }
                            val tradesNorm = (ratio * 50.0).coerceIn(0.0, 100.0)

                            val weighted = 0.4 * volNorm + 0.4 * priceNorm + 0.2 * tradesNorm
                            sentimentScore = weighted
                            needleTarget = (180f * (weighted / 100.0)).toFloat()
                        } else {
                            if (error == null) {
                                error = "HTTP ${tradesResp.code()} - ${tradesResp.message()}"
                            }
                        }
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "Unknown error"
                    } finally {
                        isLoading = false
                    }
                }
 
                // Section title outside the card
                Text(
                    text = "Market Sentiment on XIAN Network",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
 
 
                        when {
                            isLoading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Loading market data...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            error != null -> {
                                Column {
                                    Text(
                                        text = "Failed to load market data: $error",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { refreshKey++ },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Retry", color = Color.Black)
                                    }
                                }
                            }
                            else -> {
                                // Sentiment Gauge (Semicircular)
                                val scoreVal = sentimentScore ?: 0.0
                                val angleAnim by animateFloatAsState(
                                    targetValue = needleTarget,
                                    animationSpec = tween(durationMillis = 1000),
                                    label = "SentimentNeedle"
                                )
                                SentimentGauge(
                                    score = scoreVal,
                                    angleDegrees = angleAnim,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Sentiment: " + String.format("%.1f", scoreVal) + " / 100",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(12.dp))

                                // 24h Volume
                                val vol = volume ?: 0.0
                                Text(
                                    text = "24h Volume",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "%,.2f".format(vol),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = XianPrimary
                                )
 
                                Spacer(Modifier.height(12.dp))
 
                                // 24h Price Change
                                val pct = changePct ?: 0.0
                                val isUp = pct >= 0.0
                                val pctColor = if (isUp) Color(0xFF4CAF50) else Color(0xFFF44336)
                                Text(
                                    text = "24h Price Change",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = (if (isUp) "+" else "") + String.format("%.2f%%", pct),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = pctColor
                                )
                                val now = priceNow
                                val ago = price24hAgo
                                if (now != null && ago != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Now: ${String.format("%.8f", now)} • 24h ago: ${String.format("%.8f", ago)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
 
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                Spacer(Modifier.height(12.dp))
 
                                // Trades aggregation (last 50)
                                Text(
                                    text = "Trades (last 50)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                // Buys
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Buys", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                                    Text(
                                        text = "${buyCount} trades • total %,.2f USDC".format(buySumUsdc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                // Sells
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sells", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF44336))
                                    Text(
                                        text = "${sellCount} trades • total %,.2f USDC".format(sellSumUsdc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI Analysis Card
            Text(
                text = "AI Analysis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)
            )

            var aiAnalysisRequested by remember { mutableStateOf(false) }
            var isAnalyzing by remember { mutableStateOf(false) }
            var aiAnalysisResult by remember { mutableStateOf<String?>(null) }
            var aiError by remember { mutableStateOf<String?>(null) }
            val aiScope = rememberCoroutineScope()

            // Fee confirmation dialog state
            var showFeeDialog by remember { mutableStateOf(false) }
            var feeXwtToPay by remember { mutableStateOf<String?>(null) }
 
            // Authentication for fee payment (password) state
            val contextAi = LocalContext.current
            val walletManager by remember { mutableStateOf(net.xian.xianwalletapp.wallet.WalletManager.getInstance(contextAi)) }
            var showPasswordDialogAi by remember { mutableStateOf(false) }
            var passwordAi by remember { mutableStateOf("") }
            var passwordDialogError by remember { mutableStateOf<String?>(null) }
            var pendingFeeRecipient by remember { mutableStateOf<String?>(null) }
            var pendingAiStart by remember { mutableStateOf(false) }

            // Live prices for conversion: USDC -> XIAN -> XWT
            val xianUsd by walletViewModel.xianPrice.collectAsState()
            val xwtPxInXian by walletViewModel.xwtPrice.collectAsState()
            val feeUsdc = 0.04
            val neededXian = xianUsd?.let { px ->
                if (px > 0f) feeUsdc / px else null
            }
            val neededXwt = if (neededXian != null && xwtPxInXian != null && xwtPxInXian!! > 0f) {
                neededXian / xwtPxInXian!!
            } else null
            val neededXianStr = neededXian?.let { String.format(java.util.Locale.US, "%.6f", it) }
            val neededXwtStr = neededXwt?.let { String.format(java.util.Locale.US, "%.6f", it) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Portfolio Projection & Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!aiAnalysisRequested) {
                        Text(
                            text = "Get AI-powered insights on your portfolio's projected performance and personalized recommendations for each token based on current market metrics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    } else if (aiAnalysisResult != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = "AI Analysis",
                                        tint = XianPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI Insights",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = XianPrimary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = aiAnalysisResult!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    // Cyan outline style for "Request New Analysis"
                    val cyan = Color(0xFF00E5FF)
                    val isNewAnalysis = aiAnalysisRequested && aiAnalysisResult != null

                    Button(
                        onClick = {
                            if (!aiAnalysisRequested) {
                                // Show fee confirmation modal before starting AI request
                                showFeeDialog = true

                                // The actual AI launch happens in the dialog's confirm button
                            } else {
                                // Reset to request new analysis
                                aiAnalysisRequested = false
                                aiAnalysisResult = null
                                aiError = null
                                isAnalyzing = false
                            }
                        },
                        modifier = if (isNewAnalysis) {
                            Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(2.dp, cyan), RoundedCornerShape(12.dp))
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = if (isNewAnalysis) {
                            ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = cyan
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = XianPrimary,
                                contentColor = Color.Black
                            )
                        },
                        enabled = !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Analyzing Portfolio...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = if (aiAnalysisRequested && aiAnalysisResult != null)
                                    "Request New Analysis" else "Request AI Analysis",
                                color = if (isNewAnalysis) cyan else Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (aiError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OpenRouter error: ${aiError}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Fee confirmation modal
                    if (showFeeDialog) {
                        AlertDialog(
                            onDismissRequest = { showFeeDialog = false },
                            title = {
                                Text("AI Analysis Cost")
                            },
                            text = {
                                val xianPart = neededXianStr?.let { "≈ $it XIAN" } ?: "XIAN price unavailable"
                                val xwtPart = neededXwtStr?.let { "≈ $it XWT" } ?: "XWT price unavailable"
                                Text(
                                    "This action costs 0.04 USDC in XWT.\n" +
                                    "Converted: $xianPart → $xwtPart.\n\n" +
                                    "It will be deducted automatically after the analysis completes successfully."
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val feeRecipient = "d4a231d977ee7f8bd0498402b065fc61fb20189a3d36ac2a00f7de21b2276df9"
                                        feeXwtToPay = neededXwtStr
                                        val keyCached = walletManager.getUnlockedPrivateKey() != null
                                        if (!keyCached) {
                                            // Ask for authentication first; after unlock we will start AI and auto-pay
                                            pendingAiStart = true
                                            pendingFeeRecipient = feeRecipient
                                            showFeeDialog = false
                                            showPasswordDialogAi = true
                                        } else {
                                            showFeeDialog = false
                                            aiAnalysisRequested = true
                                            isAnalyzing = true
                                            aiError = null
                                            aiAnalysisResult = null
 
                                            aiScope.launch {
                                                var apiSucceeded = false
                                                try {
                                                    if (snapshot == null || snapshot.tokens.isEmpty()) {
                                                        aiAnalysisResult = generateAIAnalysis(snapshot)
                                                    } else {
                                                        val total = snapshot.totalUsd
                                                        val tokensLines = snapshot.tokens
                                                            .sortedByDescending { it.usdValue }
                                                            .joinToString(separator = "\n") { t ->
                                                                val sym = t.symbol ?: t.contract
                                                                "- $sym: ${String.format("%.2f", t.percent)}% | ${String.format("%.2f", t.usdValue)} USD"
                                                            }
                                                        val allowedTokens = snapshot.tokens.joinToString(", ") { it.symbol ?: it.contract }
                                                        val perfFinal = try { perf.lastOrNull() } catch (_: Exception) { null }
 
                                                        val systemPrompt = """
                                                            You are a cryptocurrency investment specialist.
                                                            Use only the portfolio data provided by the user.
                                                            Respond in English, extremely brief (max 80 words).
                                                            Include 2–4 relevant market emojis (e.g., 📈📉🟢🔴) to reflect trend and risk succinctly.
                                                            Format:
                                                            1) One-line summary of how the investments are doing.
                                                            2) Three numbered suggestions focused on risk, diversification, and timing.
                                                            Strict constraints:
                                                            - Do NOT recommend buying any tokens that are not in the user's portfolio list.
                                                            - Do NOT mention assets from other blockchains or outside this list.
                                                            - If you propose buy/sell/hold or rebalancing, refer only to tokens from the list by their symbol.
                                                            Avoid long disclaimers or generic filler.
                                                        """.trimIndent()
 
                                                        val userPrompt = buildString {
                                                            appendLine("Portfolio (USD total: ${String.format("%.2f", total)}):")
                                                            appendLine("Tokens:")
                                                            appendLine(tokensLines)
                                                            appendLine("Allowed tokens only: $allowedTokens")
                                                            appendLine("7d portfolio change: " + (perfFinal?.let { String.format("%.2f%%", it) } ?: "N/A"))
                                                            appendLine()
                                                            appendLine("Generate the summary and 3 suggestions now.")
                                                        }
 
                                                        val content = OpenRouterService.chatCompletion(
                                                            systemPrompt = systemPrompt,
                                                            userPrompt = userPrompt,
                                                            model = "moonshotai/kimi-k2:free",
                                                            temperature = 0.2f,
                                                            topP = 0.9f
                                                        )
                                                        aiAnalysisResult = content
                                                        apiSucceeded = true
                                                    }
                                                } catch (e: Exception) {
                                                    aiError = e.localizedMessage ?: "Unknown error"
                                                    aiAnalysisResult = generateAIAnalysis(snapshot)
                                                } finally {
                                                    if (apiSucceeded && !feeXwtToPay.isNullOrBlank()) {
                                                        val cached = walletManager.getUnlockedPrivateKey() != null
                                                        if (cached) {
                                                            try {
                                                                val res = walletViewModel.payXwtFee(
                                                                    recipientAddress = feeRecipient,
                                                                    amount = feeXwtToPay!!
                                                                )
                                                                if (!res.success) {
                                                                    aiError = (aiError?.let { "$it | " } ?: "") +
                                                                        ("Fee payment failed: " + (res.errors ?: "unknown"))
                                                                }
                                                            } catch (fe: Exception) {
                                                                aiError = (aiError?.let { "$it | " } ?: "") +
                                                                    ("Fee payment error: " + (fe.localizedMessage ?: "unknown"))
                                                            }
                                                        } else {
                                                            pendingFeeRecipient = feeRecipient
                                                            showPasswordDialogAi = true
                                                        }
                                                    }
                                                    isAnalyzing = false
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(contentColor = Color.Black),
                                    enabled = neededXwt != null
                                ) {
                                    Text("Confirm")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showFeeDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                    
// Standalone password dialog (outside the fee dialog) to ensure it shows after Confirm closes the fee modal
if (showPasswordDialogAi) {
    AlertDialog(
        onDismissRequest = {
            showPasswordDialogAi = false
            passwordAi = ""
            passwordDialogError = null
            pendingAiStart = false
            pendingFeeRecipient = null
        },
        title = { Text("Authentication Required") },
        text = {
            Column {
                Text("Enter your wallet password to proceed.")
                Spacer(modifier = Modifier.height(12.dp))
                net.xian.xianwalletapp.ui.components.PasswordTextField(
                    value = passwordAi,
                    onValueChange = { passwordAi = it; passwordDialogError = null },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!passwordDialogError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = passwordDialogError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    aiScope.launch {
                        try {
                            val unlocked = walletManager.unlockWallet(passwordAi)
                            if (unlocked == null) {
                                passwordDialogError = "Invalid password"
                                return@launch
                            }
                            // Clear dialog
                            showPasswordDialogAi = false
                            val feeRecipientLocal = pendingFeeRecipient
                            val feeAmountLocal = feeXwtToPay
                            val startAi = pendingAiStart
                            // Reset auth state
                            passwordAi = ""
                            passwordDialogError = null
                            pendingAiStart = false
                            pendingFeeRecipient = null

                            if (startAi) {
                                // Start AI now that the key is cached; fee will auto-send on success
                                aiAnalysisRequested = true
                                isAnalyzing = true
                                aiError = null
                                aiAnalysisResult = null

                                aiScope.launch {
                                    var apiSucceeded = false
                                    try {
                                        if (snapshot == null || snapshot.tokens.isEmpty()) {
                                            aiAnalysisResult = generateAIAnalysis(snapshot)
                                        } else {
                                            val total = snapshot.totalUsd
                                            val tokensLines = snapshot.tokens
                                                .sortedByDescending { it.usdValue }
                                                .joinToString(separator = "\n") { t ->
                                                    val sym = t.symbol ?: t.contract
                                                    "- $sym: ${String.format("%.2f", t.percent)}% | ${String.format("%.2f", t.usdValue)} USD"
                                                }
                                            val allowedTokens = snapshot.tokens.joinToString(", ") { it.symbol ?: it.contract }
                                            val perfFinal = try { perf.lastOrNull() } catch (_: Exception) { null }

                                            val systemPrompt = """
                                                You are a cryptocurrency investment specialist.
                                                Use only the portfolio data provided by the user.
                                                Respond in English, extremely brief (max 80 words).
                                                Include 2–4 relevant market emojis (e.g., 📈📉🟢🔴) to reflect trend and risk succinctly.
                                                Format:
                                                1) One-line summary of how the investments are doing.
                                                2) Three numbered suggestions focused on risk, diversification, and timing.
                                                Strict constraints:
                                                - Do NOT recommend buying any tokens that are not in the user's portfolio list.
                                                - Do NOT mention assets from other blockchains or outside this list.
                                                - If you propose buy/sell/hold or rebalancing, refer only to tokens from the list by their symbol.
                                                Avoid long disclaimers or generic filler.
                                            """.trimIndent()

                                            val userPrompt = buildString {
                                                appendLine("Portfolio (USD total: ${String.format("%.2f", total)}):")
                                                appendLine("Tokens:")
                                                appendLine(tokensLines)
                                                appendLine("Allowed tokens only: $allowedTokens")
                                                appendLine("7d portfolio change: " + (perfFinal?.let { String.format("%.2f%%", it) } ?: "N/A"))
                                                appendLine()
                                                appendLine("Generate the summary and 3 suggestions now.")
                                            }

                                            val content = OpenRouterService.chatCompletion(
                                                systemPrompt = systemPrompt,
                                                userPrompt = userPrompt,
                                                model = "moonshotai/kimi-k2:free",
                                                temperature = 0.2f,
                                                topP = 0.9f
                                            )
                                            aiAnalysisResult = content
                                            apiSucceeded = true
                                        }
                                    } catch (e: Exception) {
                                        aiError = e.localizedMessage ?: "Unknown error"
                                        aiAnalysisResult = generateAIAnalysis(snapshot)
                                    } finally {
                                        if (apiSucceeded && !feeXwtToPay.isNullOrBlank()) {
                                            try {
                                                val res = walletViewModel.payXwtFee(
                                                    recipientAddress = "d4a231d977ee7f8bd0498402b065fc61fb20189a3d36ac2a00f7de21b2276df9",
                                                    amount = feeXwtToPay!!
                                                )
                                                if (!res.success) {
                                                    aiError = (aiError?.let { "$it | " } ?: "") +
                                                        ("Fee payment failed: " + (res.errors ?: "unknown"))
                                                }
                                            } catch (fe: Exception) {
                                                aiError = (aiError?.let { "$it | " } ?: "") +
                                                    ("Fee payment error: " + (fe.localizedMessage ?: "unknown"))
                                            }
                                        }
                                        isAnalyzing = false
                                    }
                                }
                            } else if (!feeAmountLocal.isNullOrBlank() && !feeRecipientLocal.isNullOrBlank()) {
                                // Analysis already delivered; just pay fee now that key is cached
                                try {
                                    val res = walletViewModel.payXwtFee(
                                        recipientAddress = feeRecipientLocal!!,
                                        amount = feeAmountLocal!!
                                    )
                                    if (!res.success) {
                                        aiError = (aiError?.let { "$it | " } ?: "") +
                                            ("Fee payment failed: " + (res.errors ?: "unknown"))
                                    }
                                } catch (fe: Exception) {
                                    aiError = (aiError?.let { "$it | " } ?: "") +
                                        ("Fee payment error: " + (fe.localizedMessage ?: "unknown"))
                                }
                            }
                        } catch (e: Exception) {
                            passwordDialogError = e.localizedMessage ?: "Unknown error"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(contentColor = Color.Black),
                enabled = passwordAi.isNotBlank()
            ) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showPasswordDialogAi = false
                    passwordAi = ""
                    passwordDialogError = null
                    pendingAiStart = false
                    pendingFeeRecipient = null
                }
            ) { Text("Cancel") }
        }
    )
}
                    if (aiAnalysisRequested && aiAnalysisResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Analysis generated based on current market conditions and portfolio composition",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun generateAIAnalysis(snapshot: net.xian.xianwalletapp.ui.viewmodels.WalletViewModel.PortfolioSnapshot?): String {
    if (snapshot == null || snapshot.tokens.isEmpty()) {
        return "Unable to generate analysis - insufficient portfolio data. Please ensure your wallet has token balances and try again."
    }
    
    val totalValue = snapshot.totalUsd
    val tokenCount = snapshot.tokens.size
    val topToken = snapshot.tokens.maxByOrNull { it.usdValue }
    val diversification = if (tokenCount > 1) "diversified" else "concentrated"
    
    val projectionText = when {
        totalValue > 1000 -> "Your substantial portfolio shows strong potential for steady growth over the next 5-7 days, with moderate volatility expected."
        totalValue > 100 -> "Your portfolio demonstrates balanced growth potential with manageable risk levels in the short term."
        else -> "Your emerging portfolio has room for strategic growth with careful position sizing."
    }
    
    val recommendations = mutableListOf<String>()
    
    snapshot.tokens.sortedByDescending { it.usdValue }.take(3).forEach { token ->
        val recommendation = when {
            token.percent > 60 -> "${token.symbol ?: token.contract}: Consider partial profit-taking to reduce concentration risk and improve diversification."
            token.percent > 30 -> "${token.symbol ?: token.contract}: Strong position - maintain current allocation while monitoring for optimal rebalancing opportunities."
            token.percent > 10 -> "${token.symbol ?: token.contract}: Well-sized position - consider gradual accumulation on market dips."
            else -> "${token.symbol ?: token.contract}: Small allocation allows for strategic position building during favorable market conditions."
        }
        recommendations.add(recommendation)
    }
    
    return buildString {
        append("📊 PROJECTION: $projectionText\n\n")
        append("🎯 RECOMMENDATIONS:\n")
        recommendations.forEachIndexed { index, rec ->
            append("${index + 1}. $rec\n")
            if (index < recommendations.size - 1) append("\n")
        }
        append("\n⚠️ Risk Level: ${if (tokenCount > 3) "Low-Medium" else "Medium-High"} due to ${diversification} allocation.")
    }
}

@Composable
private fun LivePieChart(
    data: List<net.xian.xianwalletapp.ui.viewmodels.WalletViewModel.PortfolioTokenEntry>,
    highlighted: String?,
    onSliceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    var sizeState by remember { mutableStateOf(IntSize.Zero) }

    val total = data.sumOf { it.usdValue.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val angleData = remember(data) {
        var start = -90f
        data.map { e ->
            val sweep = (e.usdValue / total) * 360f
            Triple(e.contract, start, sweep).also { start += sweep }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { sizeState = it }
            .pointerInput(angleData, highlighted) {
                detectTapGestures { offset ->
                    if (sizeState.width == 0 || sizeState.height == 0) return@detectTapGestures
                    val radius = min(sizeState.width, sizeState.height) / 2.2f
                    val innerRadius = radius * 0.45f
                    val center = androidx.compose.ui.geometry.Offset(sizeState.width / 2f, sizeState.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist > radius || dist < innerRadius) return@detectTapGestures
                    var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    angle += 90f
                    if (angle < 0) angle += 360f
                    angleData.forEach { (contract, start, sweep) ->
                        val end = start + sweep
                        val normAngle = angle
                        val s = (start + 360f) % 360f
                        val e = (end + 360f) % 360f
                        val hit = if (e >= s) normAngle in s..e else normAngle >= s || normAngle <= e
                        if (hit) {
                            onSliceClick(contract)
                            return@detectTapGestures
                        }
                    }
                }
            }
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val radius = size.minDimension / 2.2f
        val innerRadius = radius * 0.45f
        val centerPt = center
        angleData.forEach { (contract, start, sweep) ->
            val color = sliceColorFor(contract)
            drawArc(
                color = if (contract == highlighted) color.copy(alpha = 0.95f) else color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = androidx.compose.ui.geometry.Offset(centerPt.x - radius, centerPt.y - radius)
            )
        }
        drawCircle(
            color = Color.Transparent,
            radius = innerRadius,
            center = centerPt,
            blendMode = BlendMode.Clear
        )
    }
}

private fun sliceColorFor(contract: String): Color = when (contract) {
    "currency" -> XianPrimary
    "con_usdc" -> Color(0xFF4CAF50)
    "con_poop_coin" -> Color(0xFF2196F3)
    "con_xtfu" -> Color(0xFFFF9800)
    "con_xarb" -> Color(0xFF9C27B0)
    "con_xwt" -> Color(0xFF00BCD4)
    else -> Color(0xFF607D8B)
}

@Composable
private fun LineChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 12.dp.toPx()

        val points = data // already oldest -> newest
        val minV = points.minOrNull() ?: 0f
        val maxV = points.maxOrNull() ?: 0f
        val range = (maxV - minV).let { if (it == 0f) 1f else it }

        val chartW = width - padding * 2
        val chartH = height - padding * 2

        val path = Path()
        points.forEachIndexed { i, v ->
            val x = padding + (i.toFloat() / (points.size - 1)) * chartW
            val y = padding + chartH - ((v - minV) / range) * chartH
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = XianPrimary,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    percentage: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = percentage,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
@Composable
private fun TokenPopularitySection(
    snapshotTokens: List<net.xian.xianwalletapp.ui.viewmodels.WalletViewModel.PortfolioTokenEntry>?,
    showCounts: Boolean
) {
    // Network source for holders count (same as TokenDetailScreen)
    val context = LocalContext.current
    val networkService = remember { XianNetworkService.getInstance(context) }
    val density = LocalDensity.current
    val labelTextSizePx = with(density) { 11.sp.toPx() }
    val labelPaddingPx = with(density) { 4.dp.toPx() }

    // Build token list from snapshot; ensure XIAN baseline is present
    val tokenMeta = remember(snapshotTokens) {
        val base = (snapshotTokens?.map { it.contract to (it.symbol ?: it.contract) } ?: emptyList()).toMutableList()
        val xianPair = "currency" to "XIAN"
        val idx = base.indexOfFirst { it.first == "currency" }
        when {
            idx == -1 -> base.add(xianPair)
            else -> base[idx] = xianPair
        }
        // De-dup by contract
        base.distinctBy { it.first }
    }

    data class PopularityItem(
        val contract: String,
        val symbol: String,
        val holders: Int,
        val percent: Float
    )
    data class PopularityUiState(
        val items: List<PopularityItem> = emptyList(),
        val xianHolders: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    // Fetch holder counts for listed tokens and the XIAN baseline
    val uiState by produceState(
        initialValue = PopularityUiState(isLoading = true),
        key1 = tokenMeta
    ) {
        if (tokenMeta.isEmpty()) {
            value = PopularityUiState(items = emptyList(), isLoading = false)
            return@produceState
        }
        try {
            val counts = mutableListOf<Pair<String, Int>>() // (contract, holders)
            for ((contract, _) in tokenMeta) {
                val c = networkService.getTokenHolders(contract)
                if (c != null) counts.add(contract to c)
            }
            val xianCount = counts.find { it.first == "currency" }?.second
                ?: (networkService.getTokenHolders("currency") ?: 0)

            val items = counts.map { (contract, count) ->
                val symbol = tokenMeta.find { it.first == contract }?.second ?: contract
                val pct = if (xianCount > 0) ((count.toFloat() / xianCount.toFloat()) * 100f).coerceAtMost(100f) else 0f
                PopularityItem(contract, symbol, count, pct)
            }.sortedByDescending { it.percent }

            value = PopularityUiState(items = items, xianHolders = xianCount, isLoading = false)
        } catch (e: Exception) {
            value = PopularityUiState(error = e.message ?: "Failed to load holders", isLoading = false)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Baseline info
        Text(
            text = "Baseline: XIAN holders = " + (if (uiState.xianHolders > 0) "%,d".format(uiState.xianHolders) else "N/A"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading holder data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error loading popularity: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tokens to display",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            else -> {
                val displayItems = remember(uiState.items) { uiState.items.take(10) } // show top 10 by popularity

                // Vertical bar chart with fixed max = 100%
                val primary = XianPrimary
                val primaryVariant = XianPrimaryVariant
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                val labelColorInt = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f).toArgb()

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val padding = 16.dp.toPx()
                    val chartW = size.width - padding * 2
                    val chartH = size.height - padding * 2

                    // Grid lines at 0, 25, 50, 75, 100
                    val gridSteps = listOf(0f, 25f, 50f, 75f, 100f)
                    gridSteps.forEach { yVal ->
                        val y = padding + chartH - (yVal / 100f) * chartH
                        drawLine(
                            color = gridColor,
                            start = androidx.compose.ui.geometry.Offset(padding, y),
                            end = androidx.compose.ui.geometry.Offset(padding + chartW, y),
                            strokeWidth = 1f
                        )
                    }

                    // Axis line (x-axis)
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(padding, padding + chartH),
                        end = androidx.compose.ui.geometry.Offset(padding + chartW, padding + chartH),
                        strokeWidth = 1.5f
                    )

                    // Bars
                    val count = displayItems.size
                    val spacing = 12.dp.toPx()
                    val barW = if (count > 0) ((chartW - spacing * (count - 1)) / count) else 0f
                    val brush = Brush.verticalGradient(listOf(primary, primaryVariant))

                    displayItems.forEachIndexed { idx, item ->
                        val h = ((item.percent / 100f) * chartH).coerceAtLeast(0f)
                        val left = padding + idx * (barW + spacing)
                        val top = padding + chartH - h
                        drawRoundRect(
                            brush = brush,
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(barW, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(x = 6.dp.toPx(), y = 6.dp.toPx())
                        )

                        if (showCounts) {
                            val centerX = left + barW / 2f
                            val baselineY = (top - labelPaddingPx).coerceAtLeast(padding + labelTextSizePx)
                            val label = "%,d".format(item.holders)
                            val paint = Paint().apply {
                                isAntiAlias = true
                                color = labelColorInt
                                textAlign = Paint.Align.CENTER
                                textSize = labelTextSizePx
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            }
                            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(label, centerX, baselineY, paint) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // X axis labels (token symbols)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    displayItems.forEach {
                        Text(
                            text = it.symbol.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                // Y axis label indicator
                Text(
                    text = "Popularity (% of XIAN holders) - fixed max 100%",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun SentimentGauge(
    score: Double,
    angleDegrees: Float,
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 120.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 24.dp
) {
    val density = LocalDensity.current
    val rPx = with(density) { radius.toPx() }
    val strokePx = with(density) { strokeWidth.toPx() }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        Canvas(
            modifier = Modifier
                .height(radius + strokeWidth + 40.dp)
                .fillMaxWidth()
        ) {
            val centerX = size.width / 2f
            val centerY = rPx + strokePx / 2f
            val left = centerX - rPx
            val top = centerY - rPx
            val diameter = rPx * 2f

            // Background track (top semicircle)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = -180f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Gradient arc across the top semicircle (left -> right)
            val brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF2196F3), // Blue
                    Color(0xFF4CAF50), // Green
                    Color(0xFFFFEB3B), // Yellow
                    Color(0xFFF44336)  // Red
                ),
                startX = left,
                endX = left + diameter
            )
            drawArc(
                brush = brush,
                startAngle = 0f,
                sweepAngle = -180f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Animated needle
            val angle = angleDegrees.coerceIn(0f, 180f)
            val rad = Math.toRadians((180f - angle).toDouble()).toFloat()
            val needleLen = rPx - strokePx
            val endX = centerX + kotlin.math.cos(rad) * needleLen
            val endY = centerY - kotlin.math.sin(rad) * needleLen

            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Center knob
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }

        // Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Cold", style = MaterialTheme.typography.labelSmall)
            Text("Neutral", style = MaterialTheme.typography.labelSmall)
            Text("Hot", style = MaterialTheme.typography.labelSmall)
        }
    }
}