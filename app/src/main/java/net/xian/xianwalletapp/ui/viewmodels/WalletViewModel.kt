package net.xian.xianwalletapp.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.xian.xianwalletapp.network.NftInfo // Keep for network response
import net.xian.xianwalletapp.data.db.NftCacheDao // Import DAO
import net.xian.xianwalletapp.data.db.NftCacheEntity // Import Entity
import net.xian.xianwalletapp.network.TokenInfo
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.network.SwapEvent // Added for chart data
import net.xian.xianwalletapp.network.XianNetworkService.PairInfo // Added for chart data
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.network.TransactionResult // Added import
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest // Import flatMapLatest
import kotlinx.coroutines.flow.flowOf // Import flowOf
import kotlinx.coroutines.launch
import org.json.JSONObject // Added import
import java.math.BigDecimal // Added import
import java.text.NumberFormat // For fee formatting
import java.util.Locale // For fee formatting
import java.time.Duration // Added for calculating remaining days
import java.time.Instant // Already imported
import net.xian.xianwalletapp.data.LocalTransactionRecord // For transaction history
import net.xian.xianwalletapp.data.TransactionRepository // For transaction history
import kotlinx.coroutines.flow.catch
// Imports para Vico Chart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.entry.FloatEntry

// Define default empty states
private val EMPTY_TOKEN_INFO_MAP: Map<String, TokenInfo> = emptyMap()
private val EMPTY_BALANCE_MAP: Map<String, Float> = emptyMap()
// private val EMPTY_NFT_LIST: List<NftInfo> = emptyList() // Replaced by Flow
private val EMPTY_NFT_CACHE_LIST: List<NftCacheEntity> = emptyList() // Default for Flow
private val EMPTY_XNS_NAME_LIST: List<String> = emptyList() // Added for XNS names
private val EMPTY_XNS_EXPIRATIONS: Map<String, Long?> = emptyMap() // Added for expirations
private val EMPTY_TRANSACTION_HISTORY: List<LocalTransactionRecord> = emptyList()

// --- Chart Data State ---
private val EMPTY_CHART_ENTRIES: List<com.patrykandpatrick.vico.core.entry.ChartEntry> = emptyList()

// --- Chart Timeframe State ---
enum class ChartTimeframe(val minutes: Int, val displayName: String) {
    FIVE_MINUTES(5, "5m"),
    FIFTEEN_MINUTES(15, "15m"),
    ONE_HOUR(60, "1h"),
    FOUR_HOURS(240, "4h")
}

private val _chartTimeframe = MutableStateFlow(ChartTimeframe.FIFTEEN_MINUTES)
val chartTimeframe: StateFlow<ChartTimeframe> = _chartTimeframe.asStateFlow()

// Data class to represent predefined tokens for easy selection
data class PredefinedToken(
    val name: String,
    val contract: String,
    val logoUrl: String? = null // Añadir URL del logo
)

// --- Fee Estimation State ---
sealed class FeeEstimationState {
    object Idle : FeeEstimationState()
    object Loading : FeeEstimationState()
    data class Success(val fee: String) : FeeEstimationState()
    // object RequiresUnlock : FeeEstimationState() // Removed: UI checks lock state before calling estimate
    object Failure : FeeEstimationState() // Network or other error
}

class WalletViewModel(
    private val walletManager: WalletManager,
    private val networkService: XianNetworkService,
    private val nftCacheDao: NftCacheDao, // Add DAO as dependency
    private val transactionRepository: TransactionRepository // Added TransactionRepository
) : ViewModel() {    // List of predefined tokens that users can select from the dropdown
    private val _internalPredefinedTokens = listOf(
        PredefinedToken(
            name = "xUSDC", 
            contract = "con_usdc",
            logoUrl = "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/ethereum/assets/0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/logo.png"
        ),
        PredefinedToken(
            name = "Poop Coin",
            contract = "con_poop_coin",
            logoUrl = "https://emojiisland.com/cdn/shop/products/Poop_Emoji_7b204f05-eec6-4496-91b1-351acc03d2c7_large.png" 
        ),
        PredefinedToken( // Add the new token here
            name = "XTFU Token", // Assuming a display name
            contract = "con_xtfu",
            logoUrl = "https://snakexchange.org/icons/con_xtfu.png" // No logo provided, will attempt to fetch later
        )
        // Add more predefined tokens here as needed
    )
    
    // Expose predefined tokens to the UI
    private val _predefinedTokens = MutableStateFlow(_internalPredefinedTokens)
    val predefinedTokens: StateFlow<List<PredefinedToken>> = _predefinedTokens.asStateFlow()

    private val _publicKeyFlow = MutableStateFlow(walletManager.getActiveWalletPublicKey() ?: "") // Use a Flow for publicKey
    val publicKey: StateFlow<String> = _publicKeyFlow.asStateFlow()

    // --- State Flows for UI ---
    private val _tokens = MutableStateFlow(walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it }))
    val tokens: StateFlow<List<String>> = _tokens.asStateFlow()

    private val _tokenInfoMap = MutableStateFlow<Map<String, TokenInfo>>(EMPTY_TOKEN_INFO_MAP)
    val tokenInfoMap: StateFlow<Map<String, TokenInfo>> = _tokenInfoMap.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, Float>>(EMPTY_BALANCE_MAP)
    val balanceMap: StateFlow<Map<String, Float>> = _balanceMap.asStateFlow()

    private val _xianPriceInfo = MutableStateFlow<Pair<Float, Float>?>(null)
    val xianPriceInfo: StateFlow<Pair<Float, Float>?> = _xianPriceInfo.asStateFlow()

    private val _xianPrice = MutableStateFlow<Float?>(null)
    val xianPrice: StateFlow<Float?> = _xianPrice.asStateFlow()

    private val _activeWalletName = MutableStateFlow<String?>(null)
    val activeWalletName: StateFlow<String?> = _activeWalletName.asStateFlow()

    // --- POOP Price State Flows ---
    private val _poopPriceInfo = MutableStateFlow<Pair<Float, Float>?>(null)
    val poopPriceInfo: StateFlow<Pair<Float, Float>?> = _poopPriceInfo.asStateFlow()

    private val _poopPrice = MutableStateFlow<Float?>(null)
    val poopPrice: StateFlow<Float?> = _poopPrice.asStateFlow()
    // --- End POOP Price State Flows ---

    // --- XTFU Price State Flows ---
    private val _xtfuPriceInfo = MutableStateFlow<Pair<Float, Float>?>(null)
    val xtfuPriceInfo: StateFlow<Pair<Float, Float>?> = _xtfuPriceInfo.asStateFlow()

    private val _xtfuPrice = MutableStateFlow<Float?>(null)
    val xtfuPrice: StateFlow<Float?> = _xtfuPrice.asStateFlow()
    // --- End XTFU Price State Flows ---

    // --- NFT List Flow from Database --- //
    // Use flatMapLatest to switch the underlying Flow when the public key changes
    val nftList: StateFlow<List<NftCacheEntity>> = _publicKeyFlow.flatMapLatest { key ->
        if (key.isNotEmpty()) {
            Log.d("WalletViewModel", "Subscribing to NFT cache for key: $key")
            nftCacheDao.getNftsByOwner(key)
        } else {
            Log.d("WalletViewModel", "Public key is empty, providing empty NFT list flow.")
            flowOf(EMPTY_NFT_CACHE_LIST) // Return a flow with an empty list if key is empty
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000), // Keep subscribed for 5s after last observer
        initialValue = EMPTY_NFT_CACHE_LIST // Initial value before flow emits
    )

    // --- State Flow for Owned XNS Names (VALID ONLY) ---
    private val _ownedXnsNames = MutableStateFlow<List<String>>(EMPTY_XNS_NAME_LIST)
    val ownedXnsNames: StateFlow<List<String>> = _ownedXnsNames.asStateFlow()

    // --- State Flow for XNS Name Expirations (Remaining Days) ---
    private val _xnsNameExpirations = MutableStateFlow<Map<String, Long?>>(EMPTY_XNS_EXPIRATIONS)
    val xnsNameExpirations: StateFlow<Map<String, Long?>> = _xnsNameExpirations.asStateFlow()
    // --- End of XNS States ---

    // --- Transaction History States ---
    private val _transactionHistory = MutableStateFlow<List<LocalTransactionRecord>>(EMPTY_TRANSACTION_HISTORY)
    val transactionHistory: StateFlow<List<LocalTransactionRecord>> = _transactionHistory.asStateFlow()

    private val _isTransactionHistoryLoading = MutableStateFlow(false)
    val isTransactionHistoryLoading: StateFlow<Boolean> = _isTransactionHistoryLoading.asStateFlow()

    private val _transactionHistoryError = MutableStateFlow<String?>(null)
    val transactionHistoryError: StateFlow<String?> = _transactionHistoryError.asStateFlow()
    // --- End of Transaction History States ---

    // --- Displayed NFT Info --- //
    // This needs adjustment. It should probably react to changes in nftList and preferred contract.
    // For simplicity now, we'll update it within loadData, but a more reactive approach is better.
    private val _displayedNftInfo = MutableStateFlow<NftCacheEntity?>(null) // Changed type to NftCacheEntity
    val displayedNftInfo: StateFlow<NftCacheEntity?> = _displayedNftInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(true) // Combined loading state for tokens/price
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isNftLoading = MutableStateFlow(false) // Separate loading for NFTs if needed
    val isNftLoading: StateFlow<Boolean> = _isNftLoading.asStateFlow()

    private val _isNodeConnected = MutableStateFlow(false)
    val isNodeConnected: StateFlow<Boolean> = _isNodeConnected.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    // --- Balance Visibility State ---
    private val _isBalanceVisible = MutableStateFlow(walletManager.isBalanceVisible())
    val isBalanceVisible: StateFlow<Boolean> = _isBalanceVisible.asStateFlow()

    private val _resolvedXnsAddress = MutableStateFlow<String?>(null)
    val resolvedXnsAddress: StateFlow<String?> = _resolvedXnsAddress.asStateFlow()

    private val _isXnsAddress = MutableStateFlow(false) // True if input is valid XNS and resolved
    val isXnsAddress: StateFlow<Boolean> = _isXnsAddress.asStateFlow()

    private val _isResolvingXns = MutableStateFlow(false)
    val isResolvingXns: StateFlow<Boolean> = _isResolvingXns.asStateFlow()    // --- Fee Estimation State Flow ---
    private val _estimatedFeeState = MutableStateFlow<FeeEstimationState>(FeeEstimationState.Idle)
    val estimatedFeeState: StateFlow<FeeEstimationState> = _estimatedFeeState.asStateFlow()
    
    private var currentStampRate: Float? = null // Cache stamp rate
    
    // --- Chart Model Producer for Vico ---
    val chartModelProducer = ChartEntryModelProducer(EMPTY_CHART_ENTRIES)
    
    private val _isChartLoading = MutableStateFlow(false)
    val isChartLoading: StateFlow<Boolean> = _isChartLoading.asStateFlow()
    
    private val _chartError = MutableStateFlow<String?>(null)
    val chartError: StateFlow<String?> = _chartError.asStateFlow()
    private val _chartNormalizationType = MutableStateFlow<String?>(null)
    val chartNormalizationType: StateFlow<String?> = _chartNormalizationType.asStateFlow()
      // Chart Y-axis range states
    private val _chartYAxisRange = MutableStateFlow<Pair<Float, Float>?>(null)
    val chartYAxisRange: StateFlow<Pair<Float, Float>?> = _chartYAxisRange.asStateFlow()
    
    // Chart Y-axis offset for better scaling
    private val _chartYAxisOffset = MutableStateFlow<Float?>(null)
    val chartYAxisOffset: StateFlow<Float?> = _chartYAxisOffset.asStateFlow()
    // --- End Chart ---

    // Flag to prevent initial load if data already exists (e.g., ViewModel survived)
    private var hasLoadedInitialData = false

    init {
        // Observe the active wallet public key flow from WalletManager
        // Ensure the initial public key state is set correctly
        _publicKeyFlow.value = walletManager.getActiveWalletPublicKey() ?: ""
        _activeWalletName.value = walletManager.getActiveWalletName() // Initialize active wallet name
        
        // Cargar información adicional de los tokens predefinidos (logos, etc)
        loadPredefinedTokensInfo()

        // Observe the active wallet public key flow for CHANGES
        viewModelScope.launch {
            var isInitialValue = true // Flag to skip reaction to the very first emission if needed
            walletManager.activeWalletPublicKeyFlow.collect { activeKey ->
                Log.d("WalletViewModel", "Observed active key change: $activeKey (isInitialValue: $isInitialValue)")

                // Only react to changes *after* the initial state is processed
                if (!isInitialValue) {
                    val newKey = activeKey ?: ""
                    val currentKey = _publicKeyFlow.value
                    if (newKey != currentKey) {
                        _publicKeyFlow.value = newKey // Update the ViewModel's public key flow
                        _activeWalletName.value = walletManager.getActiveWalletName() // Update active wallet name
                        hasLoadedInitialData = false // Reset flag to force reload for the new wallet
                        _ownedXnsNames.value = EMPTY_XNS_NAME_LIST // Clear XNS names
                        _xnsNameExpirations.value = EMPTY_XNS_EXPIRATIONS // Clear expirations
                        _transactionHistory.value = EMPTY_TRANSACTION_HISTORY // Clear transaction history
                        _transactionHistoryError.value = null // Clear errors
                        // No need to clear _nftList, the flatMapLatest will switch the source Flow
                        _displayedNftInfo.value = null // Clear displayed NFT
                        if (newKey.isNotEmpty()) {
                            loadData(force = true) // Trigger data load for the new active wallet
                            loadTransactionHistory() // Load transaction history for new key
                        } else {
                            // Handle case where all wallets might be deleted
                            Log.w("WalletViewModel", "Active wallet key became null.")
                            _tokens.value = emptyList()
                            _tokenInfoMap.value = EMPTY_TOKEN_INFO_MAP
                            _balanceMap.value = EMPTY_BALANCE_MAP
                            _isLoading.value = false
                            _isNftLoading.value = false
                            _transactionHistory.value = EMPTY_TRANSACTION_HISTORY
                            _isTransactionHistoryLoading.value = false                            // Clear chart data as well
                            chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                            _isChartLoading.value = false
                            _chartError.value = null
                            _chartYAxisRange.value = null
                            _chartYAxisOffset.value = null
                        }
                    } else {
                        // Key hasn't changed, but name might have (e.g., rename)
                        _activeWalletName.value = walletManager.getActiveWalletName()
                    }
                }
                isInitialValue = false // Mark initial value as processed
            }
        }
        // Trigger initial data load explicitly after setting up observer
        loadDataIfNotLoaded()
        loadTransactionHistory() // Load initial transaction history        // Start periodic connectivity check
        startConnectivityChecks()
    }

    // --- Public Functions for UI Interaction ---
      /**
     * Loads historical price data for the given token contract and updates the chart.
     * Fetches swap events from GraphQL and processes them into chart data.
     */
    fun loadHistoricalData(tokenContract: String) {
        viewModelScope.launch {            _isChartLoading.value = true
            _chartError.value = null
            _chartNormalizationType.value = null // Reset normalization state
            _chartYAxisRange.value = null // Reset Y-axis range
            _chartYAxisOffset.value = null // Reset Y-axis offset
            Log.d("WalletViewModel", "Loading historical data for chart: $tokenContract")

            try {                // First, we need to find the trading pair for this token
                val allPairs = networkService.getAllPairs()
                val tokenPair = allPairs.find { pair ->
                    pair.token0 == tokenContract || pair.token1 == tokenContract
                }
                
                if (tokenPair == null) {
                    _chartError.value = "No trading pair found for token $tokenContract"
                    chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                    _chartNormalizationType.value = null
                    _chartYAxisRange.value = null
                    _chartYAxisOffset.value = null
                    return@launch
                }
                
                Log.d("WalletViewModel", "Found trading pair: ${tokenPair.id} for token $tokenContract")
                
                // Fetch swap events for this pair
                val swapEvents = networkService.getSwapEventsForPair(tokenPair.id)
                
                if (swapEvents.isEmpty()) {
                    _chartError.value = "No price data available for $tokenContract"
                    chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                    _chartNormalizationType.value = null
                    _chartYAxisRange.value = null
                    _chartYAxisOffset.value = null
                    return@launch
                }

                Log.d("WalletViewModel", "Processing ${swapEvents.size} swap events for chart")

                // Process swap events into chart data
                val chartEntries = processSwapEventsToChartData(swapEvents, tokenContract, tokenPair)

                if (chartEntries.isNotEmpty()) {
                    chartModelProducer.setEntries(chartEntries)
                    Log.d("WalletViewModel", "Chart updated with ${chartEntries.size} data points")                } else {                    _chartError.value = "Could not process price data for $tokenContract"
                    chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                    _chartNormalizationType.value = null
                    _chartYAxisRange.value = null
                    _chartYAxisOffset.value = null
                }            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error loading historical chart data for $tokenContract", e)
                _chartError.value = "Failed to load chart data: ${e.message}"
                chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                _chartNormalizationType.value = null
                _chartYAxisRange.value = null
                _chartYAxisOffset.value = null
            } finally {
                _isChartLoading.value = false
            }
        }
    }

    /**
     * Processes swap events into chart data points
     */    private fun processSwapEventsToChartData(
        swapEvents: List<SwapEvent>,
        tokenContract: String,
        tokenPair: PairInfo
    ): List<FloatEntry> {        try {
            // Sort events by timestamp (oldest first for processing, will be reversed for display)
            val sortedEvents: List<SwapEvent> = swapEvents.sortedBy { event ->
                // Parse timestamp string to compare
                event.timestamp
            }

            // Use the selected timeframe
            val currentTimeframe = _chartTimeframe.value
            val candleIntervalMinutes = currentTimeframe.minutes
            val candleIntervalSeconds = candleIntervalMinutes * 60
            val timeCandles: MutableMap<Long, MutableList<SwapEvent>> = mutableMapOf()
            
            sortedEvents.forEach { event ->
                try {
                    // Parse timestamp and round to selected interval
                    val timestamp = java.time.Instant.parse(event.timestamp + "Z")
                    val candleKey = timestamp.epochSecond / candleIntervalSeconds
                    
                    timeCandles.getOrPut(candleKey) { mutableListOf() }.add(event)
                } catch (e: Exception) {
                    Log.w("WalletViewModel", "Could not parse timestamp: ${event.timestamp}")
                }
            }            // Convert candles to chart entries with volume-weighted average price (VWAP)
            val priceList: MutableList<Double> = mutableListOf()
            val chartEntries: MutableList<FloatEntry> = mutableListOf()
            var xIndex = 0f

            // Primera pasada: calcular todos los precios
            timeCandles.toSortedMap().forEach { (candleKey, events) ->
                if (events.isNotEmpty()) {
                    // Calculate volume-weighted average price for better precision
                    var totalValue = 0.0
                    var totalVolume = 0.0
                    
                    events.forEach { event ->
                        var price = event.price
                        val volume = event.volume
                        
                        // If the selected token is token1 in the pair, invert the price
                        if (tokenContract == tokenPair.token1) {
                            price = 1.0 / price
                        }
                        
                        totalValue += price * volume
                        totalVolume += volume
                    }
                    
                    // Use VWAP if we have volume, otherwise use the last price
                    val finalPrice = if (totalVolume > 0) {
                        totalValue / totalVolume
                    } else {
                        var lastPrice = events.last().price
                        if (tokenContract == tokenPair.token1) {
                            lastPrice = 1.0 / lastPrice
                        }
                        lastPrice
                    }

                    priceList.add(finalPrice)
                }
            }            // Calcular estadísticas para optimizar la escala
            if (priceList.isNotEmpty()) {
                val minPrice = priceList.minOrNull() ?: 0.0
                val maxPrice = priceList.maxOrNull() ?: 0.0
                val avgPrice = priceList.average()
                val priceRange = maxPrice - minPrice
                val relativeVariation = if (avgPrice > 0) (priceRange / avgPrice) else 0.0
                
                Log.d("WalletViewModel", "Price stats - Min: $minPrice, Max: $maxPrice, Avg: $avgPrice, Range: $priceRange, RelVar: ${relativeVariation * 100}%")
                  // Usar normalización agresiva para pequeñas variaciones
                val hasSmallVariation = relativeVariation < 0.05 && priceRange > 0 // 5% threshold
                  // Calcular padding para mejor visualización (5% arriba y abajo)
                val paddingPercent = 0.05
                val padding = priceRange * paddingPercent
                val displayMin = (minPrice - padding).coerceAtLeast(0.0) // No permitir valores negativos
                val displayMax = maxPrice + padding
                  // Set Y-axis range for the chart
                _chartYAxisRange.value = Pair(displayMin.toFloat(), displayMax.toFloat())
                    // Check if we need to use offset mapping for better Y-axis scaling
                // If price range is small relative to minimum price, we'll offset the data
                val shouldUseOffset = minPrice > 0 && relativeVariation < 0.5 && minPrice > 0.001
                val offset = if (shouldUseOffset) displayMin else 0.0
                
                // Store the offset for the chart axis formatter
                _chartYAxisOffset.value = if (shouldUseOffset) offset.toFloat() else null
                
                // Segunda pasada: crear entradas del gráfico con precios normalizados si es necesario
                // Reverse the order so the most recent data appears first (right side of chart)
                priceList.reversed().forEachIndexed { index, price ->
                    // Apply offset if needed to center the chart around the actual price range
                    val adjustedPrice = if (shouldUseOffset) (price - offset) else price
                    chartEntries.add(entryOf(xIndex, adjustedPrice.toFloat()))
                    xIndex += 1f
                }
                
                // Establecer información de escala para el eje Y
                if (shouldUseOffset) {
                    val scaleInfo = "Range: ${"%.6f".format(displayMin)} - ${"%.6f".format(displayMax)} (offset applied)"
                    _chartNormalizationType.value = scaleInfo
                    Log.d("WalletViewModel", "Using offset mapping to center chart around price range: $scaleInfo")
                } else if (hasSmallVariation) {
                    val scaleInfo = "Real Prices: ${"%.6f".format(displayMin)} - ${"%.6f".format(displayMax)}"
                    _chartNormalizationType.value = scaleInfo
                    Log.d("WalletViewModel", "Using real prices with enhanced scale for small variation (${relativeVariation * 100}%): $scaleInfo")
                } else {
                    _chartNormalizationType.value = null
                    Log.d("WalletViewModel", "Using real prices with normal scale (${relativeVariation * 100}% variation)")
                }
            }

            Log.d("WalletViewModel", "Created ${chartEntries.size} chart entries (${currentTimeframe.displayName} candles) from ${sortedEvents.size} swap events - showing most recent first")
            return chartEntries.toList()

        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error processing swap events to chart data", e)
            return emptyList<FloatEntry>()
        }
    }

    fun toggleBalanceVisibility() {
        val newVisibility = !_isBalanceVisible.value
        _isBalanceVisible.value = newVisibility
        walletManager.setBalanceVisible(newVisibility)
        Log.d("WalletViewModel", "Balance visibility set to: $newVisibility")
    }

    fun refreshData() {
        Log.d("WalletViewModel", "Manual refresh triggered.")
        // Reset token list from manager in case it changed
        _tokens.value = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
        // Force load data
        loadData(force = true)
        loadTransactionHistory(force = true) // Refresh transaction history
    }

    // Updated to accept NftCacheEntity
    fun setPreferredNft(nft: NftCacheEntity) {
        _displayedNftInfo.value = nft
        walletManager.setPreferredNftContract(nft.contractAddress)
        Log.d("WalletViewModel", "Preferred NFT set to: ${nft.contractAddress}")
    }

    fun addTokenAndRefresh(contract: String) {
        viewModelScope.launch {
            if (walletManager.addToken(contract)) {
                Log.d("WalletViewModel", "Token $contract added to WalletManager.")
                // Refresh the data after adding the token
                loadData(force = true)
            } else {
                 Log.w("WalletViewModel", "Failed to add token $contract via WalletManager.")
                 // Optionally handle the failure case (e.g., show a message)
            }
        }
    }

    fun removeToken(contract: String) {
        viewModelScope.launch {
            if (contract == "currency") return@launch // Prevent removing base currency

            if (walletManager.removeToken(contract)) {
                Log.d("WalletViewModel", "Token $contract removed from WalletManager.")
                // Update the internal list and trigger refresh
                _tokens.value = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
                loadData(force = true) // Refresh balances etc.
            } else {
                Log.w("WalletViewModel", "Failed to remove token $contract via WalletManager.")
            }
        }
    }

    /**
     * Checks if the input string could be an XNS name and attempts to resolve it.
     * Updates isXnsAddress and resolvedXnsAddress states.
     * Uses basic validation similar to the web wallet.
     */
    fun checkAndResolveXns(recipientInput: String) {
        // Reset state first
        _isXnsAddress.value = false
        _resolvedXnsAddress.value = null
        _isResolvingXns.value = false // Ensure loading is false initially

        // Basic validation (similar to web wallet)
        if (recipientInput.isBlank() || recipientInput.length < 3 || recipientInput.length > 64 || !recipientInput.matches(Regex("^[a-zA-Z0-9]+$"))) {
            Log.d("WalletViewModel", "Input '$recipientInput' is not a valid XNS name format.")
            return // Not a valid XNS format
        }

        // Looks like a potential XNS name, try to resolve
        viewModelScope.launch {
            _isResolvingXns.value = true
            try {
                val resolved = networkService.resolveXnsName(recipientInput)
                if (resolved != null) {
                    Log.d("WalletViewModel", "XNS '$recipientInput' resolved to: $resolved")
                    _resolvedXnsAddress.value = resolved
                    _isXnsAddress.value = true // Mark as successfully resolved XNS
                } else {
                    Log.d("WalletViewModel", "XNS '$recipientInput' could not be resolved.")
                    // Keep isXnsAddress false and resolvedXnsAddress null
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error resolving XNS name '$recipientInput'", e)
                // Ensure state is reset on error
                 _isXnsAddress.value = false
                 _resolvedXnsAddress.value = null
            } finally {
                _isResolvingXns.value = false
            }
        }
    }

    /**
     * Resets the XNS resolution state, typically when the input field is cleared or screen changes.
     */
     fun clearXnsResolution() {
         _resolvedXnsAddress.value = null
         _isXnsAddress.value = false
         _isResolvingXns.value = false
     }

    // --- Transaction Sending Logic ---

    // State for transaction result
    private val _transactionResult = MutableStateFlow<TransactionResult?>(null) // Correct initialization with null
    val transactionResult: StateFlow<TransactionResult?> = _transactionResult.asStateFlow()

    private val _isSendingTransaction = MutableStateFlow(false)
    val isSendingTransaction: StateFlow<Boolean> = _isSendingTransaction.asStateFlow()

    /**
     * Sends a token transfer transaction.
     * Handles nonce fetching, signing, and broadcasting.
     * Updates transactionResult and isSendingTransaction states.
     *
     * @param contract Contract address of the token.
     * @param recipientAddress The final recipient address (potentially resolved XNS).
     * @param amount The amount to send (as String to avoid precision issues before conversion).
     * @param privateKey The unlocked private key for signing.
     * @return The TransactionResult.
     */
    suspend fun sendTokenTransaction(
        contract: String,
        recipientAddress: String,
        amount: String,
        privateKey: ByteArray,
        stampLimit: Int = 500000 // Default stamp limit
    ): TransactionResult {
        _isSendingTransaction.value = true
        _transactionResult.value = null // Clear previous result

        return try {
            // Use the existing sendTransaction method in networkService
            val result = networkService.sendTransaction(
                contract = contract,
                method = "transfer",
                kwargs = JSONObject().apply { // Use imported JSONObject
                    put("to", recipientAddress)
                    // Convert amount string to BigDecimal for accuracy
                    put("amount", BigDecimal(amount)) // Use imported BigDecimal
                },
                privateKey = privateKey,
                stampLimit = stampLimit
            )
            _transactionResult.value = result // Update state with the result
            Log.d("WalletViewModel", "sendTokenTransaction result: $result")
            result // Return the result
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error in sendTokenTransaction", e)
            val errorResult = TransactionResult(success = false, errors = e.message ?: "Unknown error during transaction") // Use imported TransactionResult
            _transactionResult.value = errorResult
            errorResult // Return error result
        } finally {
            _isSendingTransaction.value = false
        }
    }

     /** Clears the transaction result state, e.g., after navigating away or showing a message. */
     fun clearTransactionResult() {
         _transactionResult.value = null
     }

    /**
     * Resets the fee estimation state back to Idle.
     * Call this after the UI has consumed the fee estimation result.
     */
    fun clearFeeEstimationState() {
        _estimatedFeeState.value = FeeEstimationState.Idle
    }

    /**
     * Requests the estimation of transaction fees (stamps).
     * Updates the estimatedFeeState flow.
     */
    fun requestFeeEstimation(contract: String, recipientAddress: String, amount: String) {
        viewModelScope.launch {
            _estimatedFeeState.value = FeeEstimationState.Loading
            val senderPublicKey = walletManager.getPublicKey() // Get public key from WalletManager
            if (senderPublicKey.isNullOrBlank()) { // Check for null or blank
                Log.e("WalletViewModel", "Cannot estimate fee: Sender public key is blank.")
                _estimatedFeeState.value = FeeEstimationState.Failure
                return@launch
            }

            // Attempt to get unlocked private key - UI should check this *before* calling.
            // If called while locked, estimateTransactionFee will likely fail during signing.
            val privateKey = walletManager.getUnlockedPrivateKey() ?: run {
                 Log.e("WalletViewModel", "requestFeeEstimation called but wallet is locked. This shouldn't happen with correct UI flow.")
                 _estimatedFeeState.value = FeeEstimationState.Failure // Treat as failure if called incorrectly
                 return@launch
            }

            try {
                // Construct kwargs
                val kwargs = JSONObject().apply {
                    put("to", recipientAddress)
                    put("amount", BigDecimal(amount)) // Use BigDecimal for accuracy
                }

                // Estimate stamps
                val estimatedStamps = networkService.estimateTransactionFee(
                    contract = contract,
                    method = "transfer",
                    kwargs = kwargs,
                    publicKey = senderPublicKey,
                    privateKey = privateKey
                )

                if (estimatedStamps == null || estimatedStamps <= 0) {
                    Log.e("WalletViewModel", "Fee estimation failed or returned invalid value: $estimatedStamps")
                    _estimatedFeeState.value = FeeEstimationState.Failure
                    return@launch
                }

                // Get stamp rate (use cached value if available)
                val rate = currentStampRate ?: try {
                    networkService.getStampRate().also { currentStampRate = it }
                } catch (e: Exception) {
                    Log.e("WalletViewModel", "Failed to get stamp rate for fee calculation", e)
                    _estimatedFeeState.value = FeeEstimationState.Failure
                    return@launch
                }

                if (rate <= 0f) {
                     Log.e("WalletViewModel", "Invalid stamp rate received: $rate")
                     _estimatedFeeState.value = FeeEstimationState.Failure
                     return@launch
                }

                // Calculate fee in XIAN (internal value)
                val feeInXianInternal = estimatedStamps.toBigDecimal() * rate.toBigDecimal()

                // Divide by 10000 ONLY for display formatting
                val feeForDisplay = feeInXianInternal.divide(BigDecimal(10000)) // Changed divisor back to 10,000

                // Format the fee for display (e.g., with 4 decimal places)
                val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
                    maximumFractionDigits = 4 // Adjust precision as needed
                    minimumFractionDigits = 2 // Show at least 2 decimals
                }
                val formattedFee = "${numberFormat.format(feeForDisplay)} XIAN"

                Log.d("WalletViewModel", "Fee estimation success: Stamps=$estimatedStamps, Rate=$rate, Fee=$formattedFee")
                _estimatedFeeState.value = FeeEstimationState.Success(formattedFee)

            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error during fee estimation request", e)
                _estimatedFeeState.value = FeeEstimationState.Failure
            }
        }
    }    /**
     * Changes the chart timeframe and reloads chart data
     */
    fun setChartTimeframe(timeframe: ChartTimeframe, tokenContract: String? = null) {
        if (_chartTimeframe.value != timeframe) {
            _chartTimeframe.value = timeframe
            Log.d("WalletViewModel", "Chart timeframe changed to: ${timeframe.displayName}")
            
            // Reload chart data with new timeframe if we have a token contract
            tokenContract?.let { contract ->
                loadHistoricalData(contract)
            }
        }
    }

    // --- Private Data Loading Logic ---
    // --- Private Data Loading Logic ---

    /**
     * Intenta cargar información adicional del token como logos para los tokens predefinidos
     */
    fun loadPredefinedTokensInfo() {
        viewModelScope.launch {
            try {
                // Crear una nueva lista mutable para los tokens actualizados
                val updatedTokens = _internalPredefinedTokens.map { token -> // Corrected => to ->
                    if (token.logoUrl == null) {
                        // Si el token no tiene logo, intentar obtener información del servicio
                        try {
                            val tokenInfo = networkService.getTokenInfo(token.contract)
                            // Crear un nuevo token con la información actualizada
                            token.copy(logoUrl = tokenInfo.logoUrl)
                        } catch (e: Exception) {
                            Log.e("WalletViewModel", "Error obteniendo info para token ${token.contract}", e)
                            token // Mantener el token original si hay error
                        }
                    } else {
                        token // Mantener el token original si ya tiene logo
                    }
                }
                
                // Actualizar la lista de tokens predefinidos
                _predefinedTokens.value = updatedTokens
                Log.d("WalletViewModel", "Lista de tokens predefinidos actualizada con información adicional")
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error al actualizar tokens predefinidos", e)
            }
        }
    }

    private fun loadDataIfNotLoaded() {
        if (!hasLoadedInitialData && _publicKeyFlow.value.isNotEmpty()) { // Only load if key exists
            Log.d("WalletViewModel", "Initial data load triggered for key: ${_publicKeyFlow.value}")
            loadData(force = false)
        } else {
             Log.d("WalletViewModel", "Skipping initial data load. Already loaded or public key empty.")
             _isLoading.value = false
             _isNftLoading.value = false
        }
    }

    private fun loadData(force: Boolean) {
        val currentPublicKey = _publicKeyFlow.value
        if (currentPublicKey.isEmpty()) {
            Log.w("WalletViewModel", "Skipping loadData, public key is empty.")
            _isLoading.value = false // Ensure loading stops if key becomes empty during load
            _isNftLoading.value = false
            return
        }

        if (!force && hasLoadedInitialData) {
            Log.d("WalletViewModel", "Skipping loadData, already loaded and not forced.")
            return
        }

        viewModelScope.launch {
            _tokens.value = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
            Log.d("WalletViewModel", "loadData: Updated token list for active wallet $currentPublicKey: ${_tokens.value}")

            Log.d("WalletViewModel", "Starting data fetch (force=$force) for key: $currentPublicKey")

            _isLoading.value = true
            _isNftLoading.value = true // Keep separate NFT loading state for now

            // Check connectivity
            _isCheckingConnection.value = true
            _isNodeConnected.value = networkService.checkNodeConnectivity()
            _isCheckingConnection.value = false
            Log.d("WalletViewModel", "Node connectivity check result: ${_isNodeConnected.value}")

            val currentTokens = _tokens.value
            val newTokenInfoMap = mutableMapOf<String, TokenInfo>()
            val newBalanceMap = mutableMapOf<String, Float>()            // Fetch Tokens (with predefined token info priority)
            currentTokens.forEach { contract ->
                try {
                    // Primero buscar en tokens predefinidos
                    val predefinedToken = _predefinedTokens.value.find { it.contract == contract }
                    
                    // Si existe en predefinidos y tiene logo, usar esa información
                    if (predefinedToken != null && predefinedToken.logoUrl != null) {
                        Log.d("WalletViewModel", "Using predefined info for $contract with logo: ${predefinedToken.logoUrl}")
                        // Crear TokenInfo a partir del token predefinido
                        val tokenInfo = TokenInfo(
                            name = predefinedToken.name,
                            symbol = predefinedToken.contract.takeLast(4).uppercase(),
                            contract = predefinedToken.contract,
                            logoUrl = predefinedToken.logoUrl
                        )
                        newTokenInfoMap[contract] = tokenInfo
                    } else {
                        // Si no está en predefinidos, obtener del blockchain
                        val tokenInfo = networkService.getTokenInfo(contract)
                        newTokenInfoMap[contract] = tokenInfo
                    }
                    
                    // Obtener balance en cualquier caso
                    val balance = networkService.getTokenBalance(contract, currentPublicKey)
                    Log.d("WalletViewModel", "Loaded balance for $contract: $balance")
                    newBalanceMap[contract] = balance
                } catch (e: Exception) {
                     Log.e("WalletViewModel", "Error fetching data for token $contract", e)
                     // Handle error case? Maybe keep old data or show error state?
                }
            }
            _tokenInfoMap.value = newTokenInfoMap
            _balanceMap.value = newBalanceMap

            // Fetch XIAN price info
            try {
                val xianPriceResult = networkService.getXianPriceInfo()
                val reserves = xianPriceResult.second // Extract the reserves pair
                _xianPriceInfo.value = reserves // Assign the reserves pair
                // Calculate XIAN price (USDC / XIAN)
                _xianPrice.value = reserves?.let { 
                    // Use .first for reserve0 (USDC) and .second for reserve1 (XIAN)
                    if (it.second != 0f) it.first / it.second else 0f
                }
                Log.d("WalletViewModel", "Fetched XIAN Price: ${_xianPrice.value} (Reserves: $reserves)")
            } catch (e: Exception) {
                 Log.e("WalletViewModel", "Error fetching XIAN price info", e)
            }

            // --- Fetch POOP Price Info --- 
            try {
                val poopInfo = networkService.getPoopPriceInfo()
                _poopPriceInfo.value = poopInfo
                // Calculate POOP price (XIAN / POOP)
                _poopPrice.value = poopInfo?.let { (reserve0_poop, reserve1_xian) ->
                    if (reserve0_poop != 0f) reserve1_xian / reserve0_poop else 0f // Note: XIAN / POOP
                }
                Log.d("WalletViewModel", "Fetched POOP Price: ${_poopPrice.value} (Reserves: $poopInfo)")
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error fetching POOP price info", e)
                _poopPrice.value = null // Ensure price is null on error
            }
            // --- End Fetch POOP Price Info ---
            
            // --- Fetch XTFU Price Info --- 
            try {
                val xtfuInfo = networkService.getXtfuPriceInfo()
                _xtfuPriceInfo.value = xtfuInfo
                // Calculate XTFU price (XIAN / XTFU)
                _xtfuPrice.value = xtfuInfo?.let { (reserve0_xtfu, reserve1_xian) ->
                    if (reserve0_xtfu != 0f) reserve1_xian / reserve0_xtfu else 0f // Note: XIAN / XTFU
                }
                Log.d("WalletViewModel", "Fetched XTFU Price: ${_xtfuPrice.value} (Reserves: $xtfuInfo)")
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error fetching XTFU price info", e)
                _xtfuPrice.value = null // Ensure price is null on error
            }
            // --- End Fetch XTFU Price Info ---


            // --- Fetch NFTs and XNS Names & Expirations --- //
            var fetchedNetworkNfts: List<NftInfo> = emptyList()
            var validXnsNames: List<String> = emptyList()
            var xnsExpirationsMap: Map<String, Long?> = emptyMap()

            // Fetch NFTs from Network
            try {
                fetchedNetworkNfts = networkService.getNfts(currentPublicKey)
                Log.d("WalletViewModel", "Fetched ${fetchedNetworkNfts.size} NFTs from network for $currentPublicKey")

                // --- Update Room Database --- //
                val nftEntities = fetchedNetworkNfts.map { nftInfo ->
                    NftCacheEntity(
                        contractAddress = nftInfo.contractAddress,
                        ownerPublicKey = currentPublicKey, // Associate with current wallet
                        name = nftInfo.name,
                        description = nftInfo.description,
                        imageUrl = nftInfo.imageUrl,
                        viewUrl = nftInfo.viewUrl
                    )
                }
                // Insert or update fetched NFTs
                nftCacheDao.insertOrUpdateNfts(nftEntities)
                Log.d("WalletViewModel", "Inserted/Updated ${nftEntities.size} NFTs into cache for $currentPublicKey")

                // Delete NFTs from cache that are no longer associated with the owner
                val currentNftContracts = fetchedNetworkNfts.map { it.contractAddress }
                nftCacheDao.deleteOrphanedNfts(currentPublicKey, currentNftContracts)
                Log.d("WalletViewModel", "Deleted orphaned NFTs from cache for $currentPublicKey")
                // --- Room Update Complete ---

            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error fetching NFTs from network or updating cache for $currentPublicKey", e)
                // Don't clear local cache on network error, just log it.
                // The UI will continue showing the last cached data.
            }

            // Fetch XNS Names
            try {
                validXnsNames = networkService.getOwnedXnsNames(currentPublicKey)
                // *** ADD LOGGING HERE ***
                Log.d("WalletViewModel", "Fetched ${validXnsNames.size} XNS names for $currentPublicKey: $validXnsNames")
                if (validXnsNames.isNotEmpty()) {
                    // Fetch expirations only if names were found
                    val expirations = networkService.getXnsNameExpirationTimes(validXnsNames)
                    // Convert Instant? to epoch seconds Long?
                    xnsExpirationsMap = expirations.mapValues { (_, instant) ->
                        instant?.epochSecond
                    }
                    Log.d("WalletViewModel", "Fetched expirations: $xnsExpirationsMap")
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error fetching XNS names or expirations", e)
                // Keep lists empty on error
                validXnsNames = emptyList()
                xnsExpirationsMap = emptyMap()
            } finally {
                 _isNftLoading.value = false // Mark NFT loading as complete (success or fail) - Moved here
            }


            // --- Update StateFlows --- //
            // Update Room (existing code)
            // ...

            // Update XNS StateFlows
            _ownedXnsNames.value = validXnsNames // Update with fetched names
            _xnsNameExpirations.value = xnsExpirationsMap // Update with fetched expirations

            // --- Update Displayed NFT --- //
            // This part needs refinement for reactivity. For now, update after network fetch.
            if (!hasLoadedInitialData || force) {
                val preferredContract = walletManager.getPreferredNftContract()
                // Get the current cached list (might have been updated by the Flow already)
                val currentCachedNfts = nftList.value
                val newDisplayedNft = if (preferredContract != null) {
                    currentCachedNfts.find { it.contractAddress == preferredContract } ?: currentCachedNfts.firstOrNull()
                } else {
                    currentCachedNfts.firstOrNull()
                }
                // Only update if the displayed NFT actually changed
                if (_displayedNftInfo.value?.contractAddress != newDisplayedNft?.contractAddress) {
                     _displayedNftInfo.value = newDisplayedNft
                     Log.d("WalletViewModel", "Updated displayed NFT (cache): ${newDisplayedNft?.contractAddress}")
                }
            }

            // Fetch initial stamp rate (unchanged)
            try {
                 currentStampRate = networkService.getStampRate()
                 Log.d("WalletViewModel", "Fetched initial stamp rate: $currentStampRate")
            } catch (e: Exception) {
                 Log.e("WalletViewModel", "Failed to fetch initial stamp rate", e)
                 currentStampRate = null // Ensure it's null if fetch fails
            }

            _isLoading.value = false // Mark overall loading as false
            // _isNftLoading is set within the NFT fetch block
            hasLoadedInitialData = true
            Log.d("WalletViewModel", "Data fetch cycle complete for $currentPublicKey.")
        }
    }

    // --- Transaction History Loading Function ---
    fun loadTransactionHistory(force: Boolean = false) {
        val currentKey = _publicKeyFlow.value
        if (currentKey.isEmpty()) {
            Log.w("WalletViewModel", "Cannot load transaction history, public key is empty.")
            _transactionHistory.value = EMPTY_TRANSACTION_HISTORY
            _isTransactionHistoryLoading.value = false
            return
        }

        if (!force && _transactionHistory.value.isNotEmpty() && _transactionHistoryError.value == null) {
            Log.d("WalletViewModel", "Transaction history already loaded and no error, skipping reload unless forced.")
            return
        }

        viewModelScope.launch {
            Log.d("WalletViewModel", "Loading transaction history for key: $currentKey")
            _isTransactionHistoryLoading.value = true
            _transactionHistoryError.value = null
            try {
                val history = transactionRepository.getNetworkTransactions(currentKey)
                _transactionHistory.value = history
                if (history.isEmpty()) {
                    Log.d("WalletViewModel", "No transaction history found for key: $currentKey")
                } else {
                    Log.d("WalletViewModel", "Loaded ${history.size} transactions for key: $currentKey")
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error loading transaction history", e)
                _transactionHistoryError.value = "Failed to load transaction history: ${e.localizedMessage}"
                _transactionHistory.value = EMPTY_TRANSACTION_HISTORY // Clear history on error
            } finally {
                _isTransactionHistoryLoading.value = false
            }
        }
    }

    // --- Private Helper Functions ---
    private fun startConnectivityChecks() {
        viewModelScope.launch {
            delay(10000) // Initial delay
            while (true) {
                _isCheckingConnection.value = true
                _isNodeConnected.value = networkService.checkNodeConnectivity()
                 Log.v("WalletViewModel", "Periodic connectivity check: ${_isNodeConnected.value}")
                _isCheckingConnection.value = false
                delay(30000) // Check every 30 seconds
            }
        }
    }
} // End of WalletViewModel class