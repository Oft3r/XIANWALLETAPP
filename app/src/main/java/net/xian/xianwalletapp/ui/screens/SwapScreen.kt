package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import java.io.IOException
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.xian.xianwalletapp.R
import net.xian.xianwalletapp.navigation.XianDestinations
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.ui.components.PasswordTextField
import net.xian.xianwalletapp.ui.theme.XianButtonType
import net.xian.xianwalletapp.ui.theme.XianPrimary
import net.xian.xianwalletapp.ui.theme.XianPrimaryVariant
import net.xian.xianwalletapp.ui.theme.xianButtonColors
import net.xian.xianwalletapp.ui.viewmodels.WalletViewModel
import net.xian.xianwalletapp.wallet.WalletManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

// Static logo mapping function for all available tokens (moved to file level for global access)
fun getTokenLogo(contract: String): Any? {
    return when (contract) {
        "currency" -> R.drawable.xian_logo
        "con_xarb" -> "file:///android_asset/xarb.jpg"
        "con_xwt" -> R.drawable.xwtlogo
        "con_xtfu" -> "https://snakexchange.org/icons/con_xtfu.png"
        "con_poop_coin" ->
                "https://emojiisland.com/cdn/shop/products/Poop_Emoji_7b204f05-eec6-4496-91b1-351acc03d2c7_large.png"
        "con_slither" -> R.drawable.sss
        "con_usdc" ->
                "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/ethereum/assets/0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/logo.png"
        "con_big_nig_with_a_cig" -> R.drawable.bignigeyes
        else -> R.drawable.ic_question_mark
    }
}

/** Screen for swapping tokens */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(
        navController: NavController,
        walletManager: WalletManager,
        networkService: XianNetworkService,
        initialFromToken: String? = null,
        initialToToken: String? = null,
        viewModel: WalletViewModel
) {
    val context = LocalContext.current
    // val snackbarHostState = remember { SnackbarHostState() }
    val toastHostState = net.xian.xianwalletapp.ui.components.rememberToastHostState()
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var fromTokenContract by remember { mutableStateOf("currency") }
    var fromTokenSymbol by remember { mutableStateOf("XIAN") }
    var toTokenContract by remember { mutableStateOf("con_usdc") }
    var toTokenSymbol by remember { mutableStateOf("USDC") }
    var fromAmount by remember { mutableStateOf("") }
    var toAmount by remember { mutableStateOf("") }
    var showFromTokenSelector by remember { mutableStateOf(false) }
    var showToTokenSelector by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var swapRate by remember { mutableStateOf<Float?>(null) }
    var priceImpact by remember { mutableStateOf<Float?>(null) }
    // Routed (two-leg) preview state
    var isRouted by remember { mutableStateOf(false) }
    var routedLeg1Rate by remember { mutableStateOf<Float?>(null) } // 1 From = XIAN
    var routedLeg2Rate by remember { mutableStateOf<Float?>(null) } // 1 XIAN = To
    var routedLeg1Impact by remember { mutableStateOf<Float?>(null) }
    var routedLeg2Impact by remember { mutableStateOf<Float?>(null) }
    var routedXianOut by remember { mutableStateOf<Float?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var isPairValid by remember { mutableStateOf(true) }
    var pairWarningMessage by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedSlippage by remember { mutableStateOf(10.0) } // Default 10%
    var showXianFeeWarning by remember { mutableStateOf(false) }
    var swapProgress by remember { mutableStateOf(0f) }
    var swapStatusMessage by remember { mutableStateOf("") }
    var unverifiedTokenMessage by remember { mutableStateOf<String?>(null) }

    // Precise balance states for SwapScreen (independent from ViewModel)
    var fromTokenPreciseBalance by remember { mutableStateOf<String?>(null) }
    var toTokenPreciseBalance by remember { mutableStateOf<String?>(null) }
    var isLoadingFromBalance by remember { mutableStateOf(false) }
    var isLoadingToBalance by remember { mutableStateOf(false) }

    // Focus state for auto-focus
    val focusRequester = remember { FocusRequester() }

    // State for 24h price changes for all tokens in selector
    var tokenPriceChanges by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var isLoadingTokenPriceChanges by remember { mutableStateOf(false) }

    // Function to get precise balance using the dedicated API
    suspend fun getPreciseTokenBalance(tokenContract: String, walletAddress: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val url =
                        "https://xian-api.poc.workers.dev/token/$tokenContract/balance/$walletAddress"
                val request =
                        Request.Builder().url(url).addHeader("accept", "application/json").build()

                val response: Response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    android.util.Log.d(
                            "SwapScreen",
                            "Precise balance for $tokenContract: $responseBody"
                    )

                    // Parse JSON response to extract balance field
                    if (responseBody != null) {
                        try {
                            val jsonObject = JSONObject(responseBody)
                            // Handle null balance properly - return null to fall back to ViewModel
                            val balance =
                                    if (jsonObject.isNull("balance")) {
                                        android.util.Log.d(
                                                "SwapScreen",
                                                "API returned null balance for $tokenContract, will fall back to ViewModel"
                                        )
                                        null
                                    } else {
                                        jsonObject.optString("balance", "0")
                                    }
                            android.util.Log.d(
                                    "SwapScreen",
                                    "Parsed balance for $tokenContract: $balance"
                            )
                            balance
                        } catch (e: Exception) {
                            android.util.Log.e(
                                    "SwapScreen",
                                    "Error parsing JSON for $tokenContract",
                                    e
                            )
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    android.util.Log.e(
                            "SwapScreen",
                            "Failed to get precise balance for $tokenContract: ${response.code}"
                    )
                    null
                }
            }
        } catch (e: IOException) {
            android.util.Log.e("SwapScreen", "Error getting precise balance for $tokenContract", e)
            null
        } catch (e: Exception) {
            android.util.Log.e(
                    "SwapScreen",
                    "Unexpected error getting precise balance for $tokenContract",
                    e
            )
            null
        }
    }

    // Helper function to get token symbol from contract
    fun getTokenSymbol(contract: String): String {
        return when (contract) {
            "currency" -> "XIAN"
            "con_usdc", "usdc" -> "USDC"
            "con_poop_coin", "poop" -> "POOP"
            "con_xtfu", "xtfu" -> "XTFU"
            "con_xarb", "xarb" -> "XARB"
            "con_xwt", "xwt" -> "XWT"
            "con_slither", "slither" -> "SSS"
            "con_big_nig_with_a_cig", "big_nig" -> "BIGNIG"
            else -> "UNKNOWN"
        }
    }

    // Helper function to format balance with appropriate precision
    fun formatBalance(balance: String?, symbol: String): String {
        if (balance == null) return "0.0 $symbol"
        val balanceValue = balance.toDoubleOrNull() ?: 0.0
        return "%.6f %s".format(Locale.US, balanceValue, symbol)
    }

    // LaunchedEffect to handle initial token selection
    LaunchedEffect(initialFromToken, initialToToken) {
        android.util.Log.d(
                "SwapScreen",
                "LaunchedEffect triggered - initialFromToken: $initialFromToken, initialToToken: $initialToToken"
        )
        if (initialFromToken != null && initialToToken != null) {
            android.util.Log.d(
                    "SwapScreen",
                    "Setting tokens - fromToken: $initialFromToken -> ${getTokenSymbol(initialFromToken)}, toToken: $initialToToken -> ${getTokenSymbol(initialToToken)}"
            )
            fromTokenContract = initialFromToken
            fromTokenSymbol = getTokenSymbol(initialFromToken)
            toTokenContract = initialToToken
            toTokenSymbol = getTokenSymbol(initialToToken)
        } else {
            android.util.Log.d("SwapScreen", "One or both tokens are null, using defaults")
        }
    }

    // Function to load precise balances
    fun loadPreciseBalances() {
        val walletAddress = walletManager.getPublicKey() ?: ""
        if (walletAddress.isNotEmpty()) {
            android.util.Log.d("SwapScreen", "Loading precise balances for wallet: $walletAddress")

            coroutineScope.launch {
                // Set loading states
                isLoadingFromBalance = true
                isLoadingToBalance = true

                try {
                    // Load fromToken balance first
                    val fromContract =
                            if (fromTokenContract == "currency") "currency" else fromTokenContract
                    fromTokenPreciseBalance = getPreciseTokenBalance(fromContract, walletAddress)
                    isLoadingFromBalance = false

                    // Small delay to avoid overwhelming the API
                    kotlinx.coroutines.delay(200)

                    // Load toToken balance after the first one completes
                    val toContract =
                            if (toTokenContract == "currency") "currency" else toTokenContract
                    toTokenPreciseBalance = getPreciseTokenBalance(toContract, walletAddress)
                    isLoadingToBalance = false

                    android.util.Log.d(
                            "SwapScreen",
                            "Precise balances loaded - From: $fromTokenPreciseBalance, To: $toTokenPreciseBalance"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("SwapScreen", "Error loading precise balances", e)
                    // Ensure loading states are cleared on error
                    isLoadingFromBalance = false
                    isLoadingToBalance = false
                }
            }
        }
    }

    // Load balances when tokens change
    LaunchedEffect(fromTokenContract, toTokenContract) { loadPreciseBalances() }

    // Auto-focus when token changes
    LaunchedEffect(fromTokenContract) {
        kotlinx.coroutines.delay(100) // Small delay to ensure UI is ready
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus request errors
        }
    }

    // Collect state from ViewModel
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val tokenInfoMap by viewModel.tokenInfoMap.collectAsStateWithLifecycle()
    val balanceMap by viewModel.balanceMap.collectAsStateWithLifecycle()
    val xianPrice by viewModel.xianPrice.collectAsStateWithLifecycle()
    val poopPrice by viewModel.poopPrice.collectAsStateWithLifecycle()
    val xtfuPrice by viewModel.xtfuPrice.collectAsStateWithLifecycle()
    val xarbPrice by viewModel.xarbPrice.collectAsStateWithLifecycle()
    val xwtPrice by viewModel.xwtPrice.collectAsStateWithLifecycle()
    val slitherPrice by viewModel.slitherPrice.collectAsStateWithLifecycle()
    val bigNigPrice by viewModel.bigNigPrice.collectAsStateWithLifecycle()

    // Helper function to check if user has enough balance for the swap
    fun hasEnoughBalance(tokenContract: String, requiredAmount: String): Boolean {
        val required = requiredAmount.toDoubleOrNull() ?: return false
        if (required <= 0) return true

        // Use precise balance if available, otherwise fall back to ViewModel balance
        val preciseBalance =
                if (tokenContract == fromTokenContract) {
                    fromTokenPreciseBalance
                } else {
                    toTokenPreciseBalance
                }

        val balance =
                if (preciseBalance != null) {
                    preciseBalance.toDoubleOrNull() ?: 0.0
                } else {
                    (balanceMap[tokenContract] ?: 0.0f).toDouble()
                }

        // For XIAN, reserve 5 tokens for fees
        val availableBalance =
                if (tokenContract == "currency") {
                    (balance - 5.0).coerceAtLeast(0.0)
                } else {
                    balance
                }

        return availableBalance >= required
    }

    // Helper function to get display balance (precise if available, otherwise ViewModel)
    fun getDisplayBalance(tokenContract: String): String? {
        val raw =
                if (tokenContract == fromTokenContract) {
                    fromTokenPreciseBalance
                } else if (tokenContract == toTokenContract) {
                    toTokenPreciseBalance
                } else {
                    balanceMap[tokenContract]?.toString()
                }
        return truncate2(raw)
    }

    // Load token price changes when the screen is first displayed
    LaunchedEffect(Unit) {
        isLoadingTokenPriceChanges = true
        val priceChanges = mutableMapOf<String, Float>()

        try {
            // Get all trading pairs
            val allPairs = networkService.getAllPairs()

            // Define available tokens locally - including currency for XIAN price change
            val tokenContracts =
                    listOf(
                            "currency",
                            "con_poop_coin",
                            "con_xtfu",
                            "con_xarb",
                            "con_xwt",
                            "con_slither",
                            "con_big_nig_with_a_cig"
                    )

            // Load price changes for each token in parallel for faster loading
            val deferredResults =
                    tokenContracts.map { contract ->
                        async {
                            try {
                                val tokenPair =
                                        allPairs.find { pair ->
                                            pair.token0 == contract || pair.token1 == contract
                                        }

                                if (tokenPair != null) {
                                    // Determine which token denomination to use
                                    val tokenDenomination =
                                            when {
                                                tokenPair.token0 == contract &&
                                                        tokenPair.token1 == "currency" ->
                                                        1 // We want XIAN per token
                                                tokenPair.token1 == contract &&
                                                        tokenPair.token0 == "currency" ->
                                                        0 // We want XIAN per token
                                                tokenPair.token0 == contract ->
                                                        1 // token1 per token0
                                                tokenPair.token1 == contract ->
                                                        0 // token0 per token1
                                                else -> 0 // default
                                            }

                                    val result =
                                            networkService.getPriceChange24h(
                                                    tokenPair.id,
                                                    tokenDenomination
                                            )
                                    if (result != null && result.isFinite()) {
                                        contract to result
                                    } else null
                                } else null
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "SwapScreen",
                                        "Error loading price change for $contract: ${e.message}"
                                )
                                null
                            }
                        }
                    }

            // Wait for all results and collect them
            deferredResults.awaitAll().filterNotNull().forEach { (contract, priceChange) ->
                priceChanges[contract] = priceChange
            }

            tokenPriceChanges = priceChanges
            android.util.Log.d("SwapScreen", "Token price changes loaded: $priceChanges")
        } catch (e: Exception) {
            android.util.Log.e("SwapScreen", "Error loading token price changes: ${e.message}")
        } finally {
            isLoadingTokenPriceChanges = false
        }
    }

    // Available tokens for swapping
    val availableTokens =
            listOf(
                    Triple("currency", "XIAN", "Xian"),
                    Triple("con_usdc", "USDC", "USD Coin"),
                    Triple("con_poop_coin", "POOP", "Poop Coin"),
                    Triple("con_xtfu", "XTFU", "XTFU Token"),
                    Triple("con_xarb", "XARB", "XARB Token"),
                    Triple("con_xwt", "XWT", "XWT Token"),
                    Triple("con_slither", "SSS", "Slither Token"),
                    Triple("con_big_nig_with_a_cig", "BIGNIG", "Big Nig Token")
            )

    // Function to validate if a direct trading pair exists
    fun isValidTradingPair(fromToken: String, toToken: String): Boolean {
        // Valid direct pairs are only between XIAN (currency) and other tokens
        return when {
            fromToken == "currency" &&
                    toToken in
                            listOf(
                                    "con_usdc",
                                    "con_poop_coin",
                                    "con_xtfu",
                                    "con_xarb",
                                    "con_xwt",
                                    "con_slither",
                                    "con_big_nig_with_a_cig"
                            ) -> true
            toToken == "currency" &&
                    fromToken in
                            listOf(
                                    "con_usdc",
                                    "con_poop_coin",
                                    "con_xtfu",
                                    "con_xarb",
                                    "con_xwt",
                                    "con_slither",
                                    "con_big_nig_with_a_cig"
                            ) -> true
            else -> false
        }
    }

    // Determine if we can route a token-to-token swap via XIAN as intermediary
    fun canRouteViaXian(fromToken: String, toToken: String): Boolean {
        if (fromToken == "currency" || toToken == "currency") return false
        // Route is possible if both tokens have valid pairs with XIAN
        return isValidTradingPair(fromToken, "currency") && isValidTradingPair("currency", toToken)
    }

    // Placeholder: estimateRoutedAmountViaXian is declared after calculatePriceImpact

    // Helper function to calculate price impact based on trade size and available liquidity
    fun calculatePriceImpact(tradeAmount: Float, fromToken: String, toToken: String): Float {
        // Get the current market price for the pair
        val currentPrice =
                when {
                    fromToken == "currency" && toToken == "con_usdc" -> xianPrice
                    fromToken == "con_usdc" && toToken == "currency" -> xianPrice?.let { 1f / it }
                    fromToken == "currency" && toToken == "con_poop_coin" ->
                            poopPrice?.let { 1f / it }
                    fromToken == "con_poop_coin" && toToken == "currency" -> poopPrice
                    fromToken == "currency" && toToken == "con_xtfu" -> xtfuPrice?.let { 1f / it }
                    fromToken == "con_xtfu" && toToken == "currency" -> xtfuPrice
                    fromToken == "currency" && toToken == "con_xarb" -> xarbPrice?.let { 1f / it }
                    fromToken == "con_xarb" && toToken == "currency" -> xarbPrice
                    fromToken == "currency" && toToken == "con_xwt" -> xwtPrice?.let { 1f / it }
                    fromToken == "con_xwt" && toToken == "currency" -> xwtPrice
                    fromToken == "currency" && toToken == "con_slither" ->
                            slitherPrice?.let { 1f / it }
                    fromToken == "con_slither" && toToken == "currency" -> slitherPrice
                    fromToken == "currency" && toToken == "con_big_nig_with_a_cig" ->
                            bigNigPrice?.let { 1f / it }
                    fromToken == "con_big_nig_with_a_cig" && toToken == "currency" -> bigNigPrice
                    else -> null
                }

        // If no price available, return 0% impact
        if (currentPrice == null) return 0f

        // Estimate liquidity depth based on token pair (in XIAN equivalent)
        val liquidityDepth =
                when {
                    (fromToken == "currency" && toToken == "con_usdc") ||
                            (fromToken == "con_usdc" && toToken == "currency") ->
                            100000f // Major pair
                    (fromToken == "currency" && toToken == "con_poop_coin") ||
                            (fromToken == "con_poop_coin" && toToken == "currency") ->
                            50000f // Medium liquidity
                    (fromToken == "currency" && toToken == "con_xtfu") ||
                            (fromToken == "con_xtfu" && toToken == "currency") ->
                            25000f // Lower liquidity
                    (fromToken == "currency" && toToken == "con_xarb") ||
                            (fromToken == "con_xarb" && toToken == "currency") ->
                            15000f // Lowest liquidity
                    (fromToken == "currency" && toToken == "con_xwt") ||
                            (fromToken == "con_xwt" && toToken == "currency") ->
                            20000f // Medium-low liquidity
                    (fromToken == "currency" && toToken == "con_slither") ||
                            (fromToken == "con_slither" && toToken == "currency") ->
                            18000f // Medium-low liquidity
                    (fromToken == "currency" && toToken == "con_big_nig_with_a_cig") ||
                            (fromToken == "con_big_nig_with_a_cig" && toToken == "currency") ->
                            12000f // Low liquidity for new token
                    else -> 10000f // Default small pool
                }

        // Convert trade amount to XIAN equivalent for comparison
        val tradeAmountInXian =
                if (fromToken == "currency") {
                    tradeAmount
                } else {
                    // Convert other token amount to XIAN equivalent using current price
                    when (fromToken) {
                        "con_usdc" -> xianPrice?.let { tradeAmount / it } ?: 0f
                        "con_poop_coin" -> poopPrice?.let { tradeAmount * it } ?: 0f
                        "con_xtfu" -> xtfuPrice?.let { tradeAmount * it } ?: 0f
                        "con_xarb" -> xarbPrice?.let { tradeAmount * it } ?: 0f
                        "con_xwt" -> xwtPrice?.let { tradeAmount * it } ?: 0f
                        "con_slither" -> slitherPrice?.let { tradeAmount * it } ?: 0f
                        "con_big_nig_with_a_cig" -> bigNigPrice?.let { tradeAmount * it } ?: 0f
                        else -> 0f
                    }
                }

        // Calculate price impact as percentage of trade size relative to liquidity
        // Formula: impact = (trade_size / liquidity_depth)^0.5 * base_impact
        val liquidityRatio = tradeAmountInXian / liquidityDepth
        val baseImpact =
                when {
                    liquidityRatio < 0.01f -> 0.1f // Very small trades: ~0.1%
                    liquidityRatio < 0.05f -> 0.5f // Small trades: ~0.5%
                    liquidityRatio < 0.1f -> 1.0f // Medium trades: ~1%
                    liquidityRatio < 0.2f -> 3.0f // Large trades: ~3%
                    else -> 8.0f // Very large trades: ~8%+
                }

        // Apply square root scaling for more realistic impact curve
        val scalingFactor = kotlin.math.sqrt(liquidityRatio.coerceAtMost(1f))
        val priceImpact = baseImpact * scalingFactor * (1f + liquidityRatio)

        // Multiply by 2 to match the web swap site's Price Impact calculation
        val adjustedImpact = priceImpact * 2f

        // Cap at 25% maximum impact
        return adjustedImpact.coerceAtMost(25f)
    }

    // Estimate routed output (A -> XIAN -> C), returning Pair(amountOut, priceImpactApprox)
    fun estimateRoutedAmountViaXian(
            amountIn: Float,
            fromToken: String,
            toToken: String
    ): Pair<Float?, Float?> {
        // First leg: fromToken -> XIAN
        val priceToXian =
                when (fromToken) {
                    "con_usdc" -> xianPrice?.let { 1f / it }
                    "con_poop_coin" -> poopPrice
                    "con_xtfu" -> xtfuPrice
                    "con_xarb" -> xarbPrice
                    "con_xwt" -> xwtPrice
                    "con_slither" -> slitherPrice
                    "con_big_nig_with_a_cig" -> bigNigPrice
                    else -> null
                }

        if (priceToXian == null) return Pair(null, null)

        val impact1 = calculatePriceImpact(amountIn, fromToken, "currency")
        val xianReceived = (amountIn * (priceToXian as Float)) * (1f - (impact1 / 100f))

        // Second leg: XIAN -> toToken
        val priceFromXian =
                when (toToken) {
                    "con_usdc" -> xianPrice
                    "con_poop_coin" -> poopPrice?.let { 1f / it }
                    "con_xtfu" -> xtfuPrice?.let { 1f / it }
                    "con_xarb" -> xarbPrice?.let { 1f / it }
                    "con_xwt" -> xwtPrice?.let { 1f / it }
                    "con_slither" -> slitherPrice?.let { 1f / it }
                    "con_big_nig_with_a_cig" -> bigNigPrice?.let { 1f / it }
                    else -> null
                }

        if (priceFromXian == null) return Pair(null, null)
        val priceFromXianF = priceFromXian as Float

        val impact2 = calculatePriceImpact(xianReceived, "currency", toToken)
        val finalOut = (xianReceived * priceFromXianF) * (1f - (impact2 / 100f))

        val combinedImpact = (impact1 + impact2).coerceAtMost(25f)
        return Pair(finalOut, combinedImpact)
    }

    // Calculate swap preview and validate pair (including routing via XIAN when needed)
    LaunchedEffect(fromAmount, fromTokenContract, toTokenContract) {
        val isFromVerified = availableTokens.any { it.first == fromTokenContract }
        val isToVerified = availableTokens.any { it.first == toTokenContract }

        if (!isFromVerified || !isToVerified) {
            isPairValid = false
            unverifiedTokenMessage =
                    "Swapping is only available for verified tokens from the list. Otherwise, please visit snakexchange.org."
            pairWarningMessage = null // Clear other warnings
            toAmount = ""
            swapRate = null
            priceImpact = null
            return@LaunchedEffect
        } else {
            unverifiedTokenMessage = null // Clear the message if tokens are valid
        }

        val directValid = isValidTradingPair(fromTokenContract, toTokenContract)
        val routedValid = !directValid && canRouteViaXian(fromTokenContract, toTokenContract)

        if (!directValid && !routedValid) {
            isPairValid = false
            pairWarningMessage = "No direct pair. This swap route is unavailable."
            toAmount = ""
            swapRate = null
            priceImpact = null
            return@LaunchedEffect
        }

        isPairValid = true
        pairWarningMessage =
                if (routedValid) "No direct pair. Routing via XIAN automatically." else null
        isRouted = routedValid
        if (!routedValid) {
            routedLeg1Rate = null
            routedLeg2Rate = null
            routedLeg1Impact = null
            routedLeg2Impact = null
            routedXianOut = null
        }

        if (fromAmount.isNotEmpty() && fromAmount.toFloatOrNull() != null) {
            // Simple swap rate calculation based on available prices
            val amount = fromAmount.toFloat()
            when {
                // Routed preview: A -> XIAN -> C
                routedValid -> {
                    // Compute per-leg preview
                    val leg1Rate =
                            when (fromTokenContract) {
                                "con_usdc" -> xianPrice?.let { 1f / it }
                                "con_poop_coin" -> poopPrice
                                "con_xtfu" -> xtfuPrice
                                "con_xarb" -> xarbPrice
                                "con_xwt" -> xwtPrice
                                "con_slither" -> slitherPrice
                                "con_big_nig_with_a_cig" -> bigNigPrice
                                else -> null
                            }
                    val leg2Rate =
                            when (toTokenContract) {
                                "con_usdc" -> xianPrice
                                "con_poop_coin" -> poopPrice?.let { 1f / it }
                                "con_xtfu" -> xtfuPrice?.let { 1f / it }
                                "con_xarb" -> xarbPrice?.let { 1f / it }
                                "con_xwt" -> xwtPrice?.let { 1f / it }
                                "con_slither" -> slitherPrice?.let { 1f / it }
                                "con_big_nig_with_a_cig" -> bigNigPrice?.let { 1f / it }
                                else -> null
                            }

                    if (leg1Rate != null && leg2Rate != null) {
                        val imp1 = calculatePriceImpact(amount, fromTokenContract, "currency")
                        val xianOut = (amount * leg1Rate) * (1f - (imp1 / 100f))

                        val imp2 = calculatePriceImpact(xianOut, "currency", toTokenContract)
                        val finalOut = (xianOut * leg2Rate) * (1f - (imp2 / 100f))

                        routedLeg1Rate = leg1Rate
                        routedLeg2Rate = leg2Rate
                        routedLeg1Impact = imp1
                        routedLeg2Impact = imp2
                        routedXianOut = xianOut

                        toAmount = "%.6f".format(Locale.US, finalOut)
                        swapRate = null
                        priceImpact = (imp1 + imp2).coerceAtMost(25f)
                    } else {
                        toAmount = ""
                        swapRate = null
                        priceImpact = null
                        routedLeg1Rate = null
                        routedLeg2Rate = null
                        routedLeg1Impact = null
                        routedLeg2Impact = null
                        routedXianOut = null
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_usdc" -> {
                    xianPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                fromTokenContract == "con_usdc" && toTokenContract == "currency" -> {
                    xianPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_poop_coin" -> {
                    poopPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "con_poop_coin" && toTokenContract == "currency" -> {
                    poopPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_xtfu" -> {
                    xtfuPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "con_xtfu" && toTokenContract == "currency" -> {
                    xtfuPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_xarb" -> {
                    xarbPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "con_xarb" && toTokenContract == "currency" -> {
                    xarbPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_xwt" -> {
                    xwtPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "con_xwt" && toTokenContract == "currency" -> {
                    xwtPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_slither" -> {
                    slitherPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "con_slither" && toTokenContract == "currency" -> {
                    slitherPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                fromTokenContract == "currency" && toTokenContract == "con_big_nig_with_a_cig" -> {
                    bigNigPrice?.let { price ->
                        val baseAmount = amount / price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = 1f / price
                    }
                }
                fromTokenContract == "con_big_nig_with_a_cig" && toTokenContract == "currency" -> {
                    bigNigPrice?.let { price ->
                        val baseAmount = amount * price
                        val calculatedImpact =
                                calculatePriceImpact(amount, fromTokenContract, toTokenContract)
                        priceImpact = calculatedImpact
                        val impactReduction = baseAmount * (calculatedImpact / 100f)
                        val finalAmount = baseAmount - impactReduction
                        toAmount = "%.6f".format(Locale.US, finalAmount)
                        swapRate = price
                    }
                }
                else -> {
                    toAmount = ""
                    swapRate = null
                    priceImpact = null
                }
            }
        } else {
            toAmount = ""
            swapRate = null
            priceImpact = null
        }
    }

    /** Perform a single swap leg: token_in -> token_out */
    suspend fun performSingleSwap(
            privateKey: ByteArray,
            tokenIn: String,
            tokenOut: String,
            amountIn: Double,
            slippage: Double,
            progressBase: Float,
            statusPrefix: String
    ): Boolean {
        // Approve amountIn * 1.1 to con_oswap
        swapProgress = progressBase
        swapStatusMessage = "$statusPrefix: Preparing approval..."

        val approveKwargs =
                JSONObject().apply {
                    put("amount", amountIn * 1.1)
                    put("to", "con_oswap")
                }

        swapProgress = (progressBase + 0.1f).coerceAtMost(0.95f)
        swapStatusMessage = "$statusPrefix: Approving tokens..."

        val approveResult =
                networkService.sendTransaction(
                        contract = tokenIn,
                        method = "approve",
                        kwargs = approveKwargs,
                        privateKey = privateKey,
                        stampLimit = 50000
                )

        if (!approveResult.success) {
            errorMessage = "Failed to approve $tokenIn: ${approveResult.errors ?: "Unknown error"}"
            return false
        }

        swapStatusMessage = "$statusPrefix: Waiting for approval..."
        kotlinx.coroutines.delay(1500)

        swapStatusMessage = "$statusPrefix: Swapping..."
        val swapKwargs =
                JSONObject().apply {
                    put("token_in", tokenIn)
                    put("token_out", tokenOut)
                    put("amount_in", amountIn)
                    put("slippage", slippage)
                    put("deadline_min", 2.0)
                }

        val swapResult =
                networkService.sendTransaction(
                        contract = "con_oswap",
                        method = "swap",
                        kwargs = swapKwargs,
                        privateKey = privateKey,
                        stampLimit = 100000
                )

        if (!swapResult.success) {
            errorMessage =
                    "Swap $tokenIn -> $tokenOut failed: ${swapResult.errors ?: "Unknown error"}"
            return false
        }
        return true
    }

    /** Performs swap. If no direct pair, route via XIAN automatically. */
    suspend fun performSwap(password: String?) {
        try {
            isLoading = true
            swapProgress = 0f
            swapStatusMessage = "Initiating swap..."

            val amount = fromAmount.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                errorMessage = "Invalid amount"
                return
            }

            // Get the private key from wallet using the same pattern as AdvancedScreen
            val needsPasswordInput = walletManager.getUnlockedPrivateKey() == null
            var keyToUse: ByteArray? = null

            if (needsPasswordInput) {
                if (password.isNullOrEmpty()) {
                    errorMessage = "Password is required"
                    return
                }
                keyToUse = walletManager.unlockWallet(password)
                if (keyToUse == null) {
                    errorMessage = "Invalid password"
                    return
                }
                println("Wallet unlocked successfully for swap transaction.")
            } else {
                keyToUse = walletManager.getUnlockedPrivateKey()
                if (keyToUse == null) {
                    errorMessage = "Wallet became locked. Please try again."
                    return
                }
                println("Using cached key for swap transaction.")
            }

            // Ensure keyToUse is non-null before proceeding
            val finalPrivateKey =
                    keyToUse
                            ?: throw IllegalStateException(
                                    "Private key acquisition failed unexpectedly."
                            )

            val directValid = isValidTradingPair(fromTokenContract, toTokenContract)
            val routedValid = !directValid && canRouteViaXian(fromTokenContract, toTokenContract)

            if (directValid) {
                // Single-leg swap
                val ok =
                        performSingleSwap(
                                privateKey = finalPrivateKey,
                                tokenIn = fromTokenContract,
                                tokenOut = toTokenContract,
                                amountIn = amount,
                                slippage = selectedSlippage,
                                progressBase = 0.2f,
                                statusPrefix = "Swap"
                        )
                if (!ok) return
                swapProgress = 1f
                swapStatusMessage = "Swap completed successfully!"
                errorMessage = "Swap completed successfully!"
                viewModel.refreshData()
                fromAmount = ""
                toAmount = ""
            } else if (routedValid) {
                // Two-leg routed swap via XIAN
                val intermediary = "currency"

                // Snapshot XIAN balance before
                val walletAddress = walletManager.getPublicKey() ?: ""
                val preXianBalanceStr =
                        getPreciseTokenBalance(intermediary, walletAddress)
                                ?: balanceMap[intermediary]?.toString()
                val preXian = preXianBalanceStr?.toDoubleOrNull() ?: 0.0

                // First leg: from -> XIAN
                val ok1 =
                        performSingleSwap(
                                privateKey = finalPrivateKey,
                                tokenIn = fromTokenContract,
                                tokenOut = intermediary,
                                amountIn = amount,
                                slippage = selectedSlippage,
                                progressBase = 0.1f,
                                statusPrefix = "Leg 1/2 (${getTokenSymbol(fromTokenContract)}→XIAN)"
                        )
                if (!ok1) return

                swapProgress = 0.55f
                swapStatusMessage = "Refreshing balances..."
                viewModel.refreshData()
                kotlinx.coroutines.delay(2000)

                // Compute XIAN delta
                val postXianBalanceStr =
                        getPreciseTokenBalance(intermediary, walletAddress)
                                ?: balanceMap[intermediary]?.toString()
                val postXian = postXianBalanceStr?.toDoubleOrNull() ?: preXian
                var xianDelta = (postXian - preXian).coerceAtLeast(0.0)

                // Reserve fees: keep at least 5 XIAN and 0.001 buffer
                val availableForLeg2 = (postXian - 5.001).coerceAtLeast(0.0)
                if (availableForLeg2 <= 0.0) {
                    errorMessage = "Insufficient XIAN to complete second leg after fees."
                    return
                }
                xianDelta = xianDelta.coerceAtMost(availableForLeg2)
                if (xianDelta <= 0.0) {
                    errorMessage = "Could not determine received XIAN for second leg."
                    return
                }

                // Second leg: XIAN -> to
                val ok2 =
                        performSingleSwap(
                                privateKey = finalPrivateKey,
                                tokenIn = intermediary,
                                tokenOut = toTokenContract,
                                amountIn = xianDelta,
                                slippage = selectedSlippage,
                                progressBase = 0.6f,
                                statusPrefix = "Leg 2/2 (XIAN→${getTokenSymbol(toTokenContract)})"
                        )
                if (!ok2) return

                swapProgress = 1f
                swapStatusMessage = "Cross-swap completed successfully!"
                errorMessage = "Swap completed successfully!"
                viewModel.refreshData()
                fromAmount = ""
                toAmount = ""
            } else {
                errorMessage = "This swap route is unavailable."
                return
            }
        } catch (e: Exception) {
            swapProgress = 0f
            swapStatusMessage = ""
            errorMessage = "Error during swap: ${e.message}"
        } finally {
            isLoading = false
            showPasswordDialog = false
            // Reset progress after a delay
            kotlinx.coroutines.delay(2000)
            swapProgress = 0f
            swapStatusMessage = ""
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        titleContentColor = MaterialTheme.colorScheme.primary
                                ),
                        title = {
                            Surface(
                                    modifier =
                                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                        text = "Swap Tokens",
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 2.dp
                                                ),
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
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Swap Settings"
                                )
                            }
                        }
                )
            }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            net.xian.xianwalletapp.ui.components.TopToastHost(
                    state = toastHostState,
                    modifier = Modifier.align(Alignment.TopEnd)
            )
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .background(
                                            brush =
                                                    Brush.verticalGradient(
                                                            colors =
                                                                    listOf(
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .background,
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .background
                                                                                    .copy(
                                                                                            alpha =
                                                                                                    0.95f
                                                                                    )
                                                                    )
                                                    )
                                    )
                                    .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // From Token Section
                Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "From",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Max clickable text
                            Text(
                                    text = "Max",
                                    color = XianPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier =
                                            Modifier.clickable {
                                                        // Use the same balance logic as validation
                                                        val balance =
                                                                if (fromTokenPreciseBalance != null
                                                                ) {
                                                                    fromTokenPreciseBalance!!
                                                                            .toDoubleOrNull()
                                                                            ?: 0.0
                                                                } else {
                                                                    (balanceMap[fromTokenContract]
                                                                                    ?: 0.0f)
                                                                            .toDouble()
                                                                }

                                                        val maxAmount =
                                                                if (fromTokenContract == "currency"
                                                                ) {
                                                                    // For XIAN, subtract 5 for fees
                                                                    // but also apply -0.001
                                                                    val maxAvailable =
                                                                            (balance - 5.0 - 0.001)
                                                                                    .coerceAtLeast(
                                                                                            0.0
                                                                                    )
                                                                    showXianFeeWarning =
                                                                            balance > 5.0 &&
                                                                                    maxAvailable >
                                                                                            0.0
                                                                    maxAvailable
                                                                } else {
                                                                    // For other tokens, subtract
                                                                    // 0.001 from balance
                                                                    showXianFeeWarning = false
                                                                    (balance - 0.001).coerceAtLeast(
                                                                            0.0
                                                                    )
                                                                }

                                                        fromAmount =
                                                                if (maxAmount > 0.0) {
                                                                    // Truncate to 3 decimals (no
                                                                    // rounding) for Max button
                                                                    truncate3(maxAmount.toString())
                                                                } else {
                                                                    "0"
                                                                }
                                                    }
                                                    .padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Token selector
                            Row(
                                    modifier =
                                            Modifier.clickable { showFromTokenSelector = true }
                                                    .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Use static logo mapping function (same as token selector)
                                AsyncImage(
                                        model = getTokenLogo(fromTokenContract),
                                        imageLoader = viewModel.getImageLoader(),
                                        contentDescription = "$fromTokenSymbol Logo",
                                        modifier =
                                                Modifier.size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.1f)
                                                        ),
                                        contentScale = ContentScale.Inside,
                                        error =
                                                if (fromTokenContract == "currency")
                                                        painterResource(id = R.drawable.xian_logo)
                                                else
                                                        painterResource(
                                                                id = R.drawable.ic_question_mark
                                                        ),
                                        placeholder =
                                                if (fromTokenContract == "currency")
                                                        painterResource(id = R.drawable.xian_logo)
                                                else
                                                        painterResource(
                                                                id = R.drawable.ic_question_mark
                                                        )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                        text = fromTokenSymbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )

                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select token"
                                )
                            }

                            // Amount input
                            OutlinedTextField(
                                    value = fromAmount,
                                    onValueChange = {
                                        fromAmount = it
                                        showXianFeeWarning =
                                                false // Hide warning when user types manually
                                    },
                                    modifier =
                                            Modifier.width(150.dp).focusRequester(focusRequester),
                                    placeholder = {
                                        Text(
                                                text = "0.0",
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    keyboardOptions =
                                            KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    textStyle =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                    textAlign = TextAlign.End
                                            ),
                                    colors =
                                            OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Transparent,
                                                    unfocusedBorderColor = Color.Transparent,
                                                    disabledBorderColor = Color.Transparent,
                                                    errorBorderColor = Color.Transparent
                                            )
                            )
                        }

                        // Balance display with precise balance system
                        Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoadingFromBalance) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            val displayBalance = getDisplayBalance(fromTokenContract)
                            val hasEnough = hasEnoughBalance(fromTokenContract, fromAmount)

                            Text(
                                    text = "Balance: $displayBalance $fromTokenSymbol",
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                            if (!hasEnough &&
                                                            fromAmount.isNotEmpty() &&
                                                            fromAmount != "0"
                                            ) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    modifier = Modifier.weight(1f)
                            )

                            // Warning for insufficient balance
                            if (!hasEnough && fromAmount.isNotEmpty() && fromAmount != "0") {
                                Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Insufficient balance",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Swap direction button
                Box(
                        modifier =
                                Modifier.size(48.dp)
                                        .background(
                                                brush =
                                                        Brush.horizontalGradient(
                                                                colors =
                                                                        listOf(
                                                                                XianPrimary,
                                                                                XianPrimaryVariant
                                                                        )
                                                        ),
                                                shape = RoundedCornerShape(24.dp)
                                        )
                                        .clickable {
                                            // Swap the tokens
                                            val tempContract = fromTokenContract
                                            val tempSymbol = fromTokenSymbol
                                            fromTokenContract = toTokenContract
                                            fromTokenSymbol = toTokenSymbol
                                            toTokenContract = tempContract
                                            toTokenSymbol = tempSymbol

                                            // Clear amounts
                                            fromAmount = ""
                                            toAmount = ""
                                        },
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap tokens",
                            tint = Color.Black
                    )
                }

                // To Token Section
                Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                text = "To",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Token selector
                            Row(
                                    modifier =
                                            Modifier.clickable { showToTokenSelector = true }
                                                    .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Use static logo mapping function (same as token selector)
                                AsyncImage(
                                        model = getTokenLogo(toTokenContract),
                                        imageLoader = viewModel.getImageLoader(),
                                        contentDescription = "$toTokenSymbol Logo",
                                        modifier =
                                                Modifier.size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.1f)
                                                        ),
                                        contentScale = ContentScale.Inside,
                                        error =
                                                if (toTokenContract == "currency")
                                                        painterResource(id = R.drawable.xian_logo)
                                                else
                                                        painterResource(
                                                                id = R.drawable.ic_question_mark
                                                        ),
                                        placeholder =
                                                if (toTokenContract == "currency")
                                                        painterResource(id = R.drawable.xian_logo)
                                                else
                                                        painterResource(
                                                                id = R.drawable.ic_question_mark
                                                        )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                        text = toTokenSymbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )

                                Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Select token"
                                )
                            }

                            // Amount display
                            Text(
                                    text = toAmount.ifEmpty { "0.0" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 16.dp)
                            )
                        }

                        // Balance display with precise balance system
                        Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoadingToBalance) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            val displayBalance = getDisplayBalance(toTokenContract)

                            Text(
                                    text = "Balance: $displayBalance $toTokenSymbol",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Swap rate and price impact display (direct)
                if (swapRate != null && fromAmount.isNotEmpty() && !isRouted) {
                    Card(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .border(
                                                    width = 1.dp,
                                                    color = XianPrimary,
                                                    shape = RoundedCornerShape(8.dp)
                                            ),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "Swap Details",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Swap Rate
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = "Rate:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                        text =
                                                "1 $fromTokenSymbol = ${String.format(Locale.US, "%.6f", swapRate)} $toTokenSymbol",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Price Impact
                            priceImpact?.let { impact ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                            text = "Price Impact:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                            text = "${String.format(Locale.US, "%.2f", impact)}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color =
                                                    when {
                                                        impact < 1f -> Color.Green
                                                        impact < 3f -> Color(0xFFFF9800) // Orange
                                                        else -> Color.Red
                                                    }
                                    )
                                }
                            }
                        }
                    }
                }

                // Routed two-leg details (shown when auto-routing via XIAN)
                if (isRouted && fromAmount.isNotEmpty()) {
                    Card(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .border(
                                                    width = 1.dp,
                                                    color = XianPrimary,
                                                    shape = RoundedCornerShape(8.dp)
                                            ),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "Swap Details (Routed)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Route line
                            Text(
                                    text = "Route: $fromTokenSymbol → XIAN → $toTokenSymbol",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            // Leg 1
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = "Leg 1: $fromTokenSymbol → XIAN",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = "Rate:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                        text =
                                                routedLeg1Rate?.let {
                                                    "1 $fromTokenSymbol = ${String.format(Locale.US, "%.6f", it)} XIAN"
                                                }
                                                        ?: "--",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = "Price Impact:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val imp1 = routedLeg1Impact
                                Text(
                                        text =
                                                if (imp1 != null)
                                                        "${String.format(Locale.US, "%.2f", imp1)}%"
                                                else "--",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                                when {
                                                    imp1 == null ->
                                                            MaterialTheme.colorScheme
                                                                    .onPrimaryContainer
                                                    imp1 < 1f -> Color.Green
                                                    imp1 < 3f -> Color(0xFFFF9800)
                                                    else -> Color.Red
                                                }
                                )
                            }
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = "Est. Out:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                        text =
                                                routedXianOut?.let {
                                                    "${String.format(Locale.US, "%.6f", it)} XIAN"
                                                }
                                                        ?: "--",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Leg 2
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = "Leg 2: XIAN → $toTokenSymbol",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = "Rate:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                        text =
                                                routedLeg2Rate?.let {
                                                    "1 XIAN = ${String.format(Locale.US, "%.6f", it)} $toTokenSymbol"
                                                }
                                                        ?: "--",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = "Price Impact:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val imp2 = routedLeg2Impact
                                Text(
                                        text =
                                                if (imp2 != null)
                                                        "${String.format(Locale.US, "%.2f", imp2)}%"
                                                else "--",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                                when {
                                                    imp2 == null ->
                                                            MaterialTheme.colorScheme
                                                                    .onPrimaryContainer
                                                    imp2 < 1f -> Color.Green
                                                    imp2 < 3f -> Color(0xFFFF9800)
                                                    else -> Color.Red
                                                }
                                )
                            }

                            // Combined price impact
                            priceImpact?.let { impact ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                            text = "Combined Impact:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                            text = "${String.format(Locale.US, "%.2f", impact)}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color =
                                                    when {
                                                        impact < 1f -> Color.Green
                                                        impact < 3f -> Color(0xFFFF9800)
                                                        else -> Color.Red
                                                    }
                                    )
                                }
                            }
                        }
                    }
                }

                // Unverified token warning
                if (unverifiedTokenMessage != null) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.errorContainer
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "⚠️ Warning",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val annotatedString = buildAnnotatedString {
                                append(
                                        "Swapping is only available for verified tokens from the list. Otherwise, please visit "
                                )
                                pushStringAnnotation(
                                        tag = "URL",
                                        annotation = "https://snakexchange.org"
                                )
                                withStyle(
                                        style =
                                                SpanStyle(
                                                        color = Color.Blue,
                                                        textDecoration = TextDecoration.Underline
                                                )
                                ) { append("snakexchange.org") }
                                pop()
                                append(".")
                            }

                            ClickableText(
                                    text = annotatedString,
                                    style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .onErrorContainer
                                            ),
                                    onClick = { offset ->
                                        annotatedString
                                                .getStringAnnotations(
                                                        tag = "URL",
                                                        start = offset,
                                                        end = offset
                                                )
                                                .firstOrNull()
                                                ?.let { annotation ->
                                                    navController.navigate(
                                                            "${XianDestinations.WEB_BROWSER}?url=${annotation.item}"
                                                    )
                                                }
                                    }
                            )
                        }
                    }
                }

                // Pair validation warning
                if (!isPairValid && pairWarningMessage != null) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.errorContainer
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "⚠️ Warning",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                    text = pairWarningMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // XIAN fee warning - only show when swap details are visible
                if (showXianFeeWarning && swapRate != null && fromAmount.isNotEmpty()) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = "⚠️ Fee Reserve",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                    text =
                                            "5 XIAN are reserved from your maximum balance for fees and future operations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Powered by text with logo
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Powered by ", color = Color.Gray, fontSize = 12.sp)
                    Image(
                            painter = painterResource(id = R.drawable.snakex),
                            contentDescription = "SnakeX Logo",
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(Color.Gray)
                    )
                    Text(" snakexchange.org and XIAN", color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator during swap
                if (isLoading && swapProgress > 0f) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.7f
                                                    )
                                    ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                    text = swapStatusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            LinearProgressIndicator(
                                    progress = swapProgress,
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                    color = XianPrimary,
                                    trackColor =
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )

                            Text(
                                    text = "${(swapProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    textAlign = TextAlign.End
                            )
                        }
                    }
                }

                // Swap button
                val hasEnoughBalance = hasEnoughBalance(fromTokenContract, fromAmount)
                val isButtonEnabled =
                        fromAmount.isNotEmpty() &&
                                fromAmount.toFloatOrNull() != null &&
                                !isLoading &&
                                isPairValid &&
                                hasEnoughBalance

                Button(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = isButtonEnabled,
                        colors = xianButtonColors(XianButtonType.PRIMARY)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                        )
                    } else {
                        val buttonText =
                                when {
                                    !hasEnoughBalance &&
                                            fromAmount.isNotEmpty() &&
                                            fromAmount != "0" -> "Insufficient Balance"
                                    !isPairValid -> "Invalid Pair"
                                    else -> "Swap Tokens"
                                }
                        Text(text = buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Error message
                errorMessage?.let { message ->
                    LaunchedEffect(message) {
                        val toastType =
                                if (message.contains("Swap completed successfully")) {
                                    net.xian.xianwalletapp.ui.components.ToastType.Success
                                } else {
                                    net.xian.xianwalletapp.ui.components.ToastType.Error
                                }
                        toastHostState.show(message, toastType)
                        errorMessage = null
                    }
                }
            }
        }

        // Token selection dropdowns
        if (showFromTokenSelector) {
            TokenSelectorDialog(
                    tokens = availableTokens,
                    selectedContract = fromTokenContract,
                    tokenInfoMap = tokenInfoMap,
                    imageLoader = viewModel.getImageLoader(),
                    tokenPriceChanges = tokenPriceChanges,
                    isLoadingPriceChanges = isLoadingTokenPriceChanges,
                    onTokenSelected = { contract, symbol ->
                        if (contract != toTokenContract) {
                            fromTokenContract = contract
                            fromTokenSymbol = symbol
                            fromAmount = ""
                            toAmount = ""
                        }
                        showFromTokenSelector = false
                    },
                    onDismiss = { showFromTokenSelector = false }
            )
        }

        if (showToTokenSelector) {
            TokenSelectorDialog(
                    tokens = availableTokens,
                    selectedContract = toTokenContract,
                    tokenInfoMap = tokenInfoMap,
                    imageLoader = viewModel.getImageLoader(),
                    tokenPriceChanges = tokenPriceChanges,
                    isLoadingPriceChanges = isLoadingTokenPriceChanges,
                    onTokenSelected = { contract, symbol ->
                        if (contract != fromTokenContract) {
                            toTokenContract = contract
                            toTokenSymbol = symbol
                            fromAmount = ""
                            toAmount = ""
                        }
                        showToTokenSelector = false
                    },
                    onDismiss = { showToTokenSelector = false }
            )
        }

        // Password dialog for wallet unlocking
        if (showPasswordDialog) {
            val needsPasswordInput = walletManager.getUnlockedPrivateKey() == null

            PasswordPromptDialog(
                    showPasswordField = needsPasswordInput,
                    onDismiss = { showPasswordDialog = false },
                    onConfirm = { password ->
                        showPasswordDialog = false
                        coroutineScope.launch { performSwap(password) }
                    }
            )
        }

        // Settings dialog
        if (showSettingsDialog) {
            SwapSettingsDialog(
                    currentSlippage = selectedSlippage,
                    onSlippageSelected = { slippage -> selectedSlippage = slippage },
                    onDismiss = { showSettingsDialog = false },
                    onSave = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun TokenSelectorDialog(
        tokens: List<Triple<String, String, String>>,
        selectedContract: String,
        tokenInfoMap: Map<String, Any>,
        imageLoader: ImageLoader,
        tokenPriceChanges: Map<String, Float>,
        isLoadingPriceChanges: Boolean,
        onTokenSelected: (String, String) -> Unit,
        onDismiss: () -> Unit
) {
    // Use static logo mapping function from file level
    val percentageFormatter = DecimalFormat("#,##0.00") // For percentage values (2 decimals)

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Token") },
            text = {
                Column {
                    tokens.forEach { (contract, symbol, name) ->
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable { onTokenSelected(contract, symbol) }
                                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                                .then(
                                                        if (contract == selectedContract) {
                                                            Modifier.border(
                                                                    width = 1.dp,
                                                                    color = XianPrimary,
                                                                    shape = RoundedCornerShape(8.dp)
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                )
                                                .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Use static logo mapping that doesn't depend on WalletScreen tokens
                            val logoModel = getTokenLogo(contract)

                            AsyncImage(
                                    model = logoModel,
                                    imageLoader = imageLoader,
                                    contentDescription = "$name Logo",
                                    modifier =
                                            Modifier.size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.1f
                                                            )
                                                    ),
                                    contentScale = ContentScale.Inside,
                                    error = painterResource(id = R.drawable.ic_question_mark),
                                    placeholder = painterResource(id = R.drawable.ic_question_mark)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = symbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                                Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Show 24h price change percentage - hide for USDC, show for others
                            // including XIAN
                            if (contract != "con_usdc") {
                                val priceChange = tokenPriceChanges[contract]
                                when {
                                    isLoadingPriceChanges -> {
                                        // Show loading indicator while price changes are loading
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    priceChange != null && priceChange.isFinite() -> {
                                        val isPositive = priceChange >= 0
                                        val changeColor =
                                                if (isPositive) Color(0xFF4CAF50)
                                                else Color(0xFFF44336)
                                        val changeText =
                                                if (isPositive)
                                                        "+${percentageFormatter.format(priceChange)}%"
                                                else "${percentageFormatter.format(priceChange)}%"

                                        Text(
                                                text = changeText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = changeColor,
                                                fontWeight = FontWeight.Medium
                                        )
                                    }
                                    else -> {
                                        // Show placeholder when no data is available
                                        Text(
                                                text = "--",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) { Text("Cancel") }
            }
    )
}

@Composable
private fun PasswordPromptDialog(
        showPasswordField: Boolean, // Add parameter to control password field visibility
        onConfirm: (String?) -> Unit, // Password might be null if not shown
        onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                if (showPasswordField) Text("Password Required") else Text("Confirm Transaction")
            }, // Adjust title
            text = {
                Column {
                    if (showPasswordField) {
                        Text("Wallet is locked. Please enter your password to proceed.")
                        Spacer(modifier = Modifier.height(8.dp))
                        PasswordTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Confirm swap transaction?") // Confirmation text when unlocked
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = { onConfirm(if (showPasswordField) password else null) },
                        colors = ButtonDefaults.buttonColors(contentColor = Color.Black)
                ) { Text("Confirm") }
            },
            dismissButton = {
                Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(contentColor = Color.Black)
                ) { Text("Cancel") }
            }
    )
}

@Composable
private fun SwapSettingsDialog(
        currentSlippage: Double,
        onSlippageSelected: (Double) -> Unit,
        onDismiss: () -> Unit,
        onSave: () -> Unit
) {
    var tempSlippage by remember { mutableStateOf(currentSlippage) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                        text = "Swap Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                            text = "Slippage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                            text =
                                    "The maximum allowable difference between the estimated price and the actual price. If the price difference exceeds this limit, the swap will automatically be reverted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Slippage buttons
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3.0, 5.0, 10.0).forEach { slippage ->
                            Button(
                                    onClick = { tempSlippage = slippage },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                            if (tempSlippage == slippage) {
                                                ButtonDefaults.buttonColors(
                                                        containerColor = XianPrimary,
                                                        contentColor = Color.Black
                                                )
                                            } else {
                                                ButtonDefaults.outlinedButtonColors(
                                                        containerColor = Color.Transparent,
                                                        contentColor =
                                                                MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                    border =
                                            if (tempSlippage != slippage) {
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline
                                                )
                                            } else null
                            ) { Text("${slippage.toInt()}%") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = {
                            onSlippageSelected(tempSlippage)
                            onSave()
                        },
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = XianPrimary,
                                        contentColor = Color.Black
                                )
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) { Text("Cancel") }
            }
    )
}

// --- Truncation Utilities (no rounding) ---
private fun truncateDecimalString(value: String?, scale: Int): String {
    if (value.isNullOrBlank())
            return "0".let { if (scale > 0) it + "." + "0".repeat(scale) else it }
    // Use BigDecimal for safety; fallback to original string if parsing fails
    return try {
        // Normalize via BigDecimal to remove scientific notation, then manually truncate
        val bd = value.trim().let { BigDecimal(it) }
        val plain = bd.stripTrailingZeros().toPlainString()
        val parts = plain.split('.')
        if (parts.size == 1) {
            // No decimal part
            if (scale == 0) parts[0] else parts[0] + "." + "0".repeat(scale)
        } else {
            val intPart = parts[0]
            val fracPart = parts[1]
            val truncatedFrac =
                    if (fracPart.length >= scale) fracPart.substring(0, scale)
                    else fracPart + "0".repeat(scale - fracPart.length)
            if (scale == 0) intPart else intPart + "." + truncatedFrac
        }
    } catch (e: Exception) {
        // Fallback: naive manual approach without BigDecimal
        val raw = value.trim()
        val dotIndex = raw.indexOf('.')
        if (dotIndex == -1) {
            if (scale == 0) raw else raw + "." + "0".repeat(scale)
        } else {
            val intPart = raw.substring(0, dotIndex)
            val fracPart = raw.substring(dotIndex + 1)
            val truncatedFrac =
                    if (fracPart.length >= scale) fracPart.substring(0, scale)
                    else fracPart + "0".repeat(scale - fracPart.length)
            if (scale == 0) intPart else intPart + "." + truncatedFrac
        }
    }
}

private fun truncate2(value: String?): String = truncateDecimalString(value, 2)

private fun truncate3(value: String?): String = truncateDecimalString(value, 3)
