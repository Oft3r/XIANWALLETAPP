package net.xian.xianwalletapp.ui.viewmodels

// Imports para Vico Chart
// ImprovedTokenLogoCacheManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryOf
import java.math.BigDecimal // Added import
import java.text.NumberFormat // For fee formatting
import java.time.Instant // Already imported
import java.util.Locale // For fee formatting
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers // Added for IO dispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine // For portfolio snapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest // Import flatMapLatest
import kotlinx.coroutines.flow.flowOf // Import flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Added for background operations
import net.xian.xianwalletapp.data.ImprovedTokenLogoCacheManager // Import
import net.xian.xianwalletapp.data.LocalTransactionRecord // For transaction history
import net.xian.xianwalletapp.data.NftImageCacheManager
import net.xian.xianwalletapp.data.TokenPriceRepository // For price caching
import net.xian.xianwalletapp.data.TransactionRepository // For transaction history
import net.xian.xianwalletapp.data.db.NftCacheDao // Import DAO
import net.xian.xianwalletapp.data.db.NftCacheEntity // Import Entity
import net.xian.xianwalletapp.data.db.TokenCacheDao // Import TokenCacheDao
import net.xian.xianwalletapp.data.db.TokenCacheEntity // Import TokenCacheEntity
import net.xian.xianwalletapp.network.NftInfo // Keep for network response
import net.xian.xianwalletapp.network.SwapEvent // Added for chart data
import net.xian.xianwalletapp.network.TokenInfo
import net.xian.xianwalletapp.network.TransactionResult // Added import
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.network.XianNetworkService.PairInfo // Added for chart data
import net.xian.xianwalletapp.wallet.WalletManager
import org.json.JSONObject // Added import

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
        val logoUrl: String? = null, // Añadir URL del logo
        val symbol: String? = null // Símbolo personalizado del token
)

// --- Fee Estimation State ---
sealed class FeeEstimationState {
    object Idle : FeeEstimationState()
    object Loading : FeeEstimationState()
    data class Success(val fee: String) : FeeEstimationState()
    // object RequiresUnlock : FeeEstimationState() // Removed: UI checks lock state before calling
    // estimate
    object Failure : FeeEstimationState() // Network or other error
}

class WalletViewModel(
        private val context: Context,
        private val walletManager: WalletManager,
        private val networkService: XianNetworkService,
        private val nftCacheDao: NftCacheDao, // Add DAO as dependency
        private val tokenCacheDao: TokenCacheDao, // Add TokenCacheDao as dependency
        private val transactionRepository: TransactionRepository, // Added TransactionRepository
        private val tokenPriceRepository: TokenPriceRepository // Added TokenPriceRepository
) : ViewModel() {
    companion object {
        private const val DEBUG_PERF = true // set false to silence attribution debug logs
    }

    // Initialize ImprovedTokenLogoCacheManager for permanent image caching
    private val tokenLogoCacheManager = ImprovedTokenLogoCacheManager(context)
    // Initialize dedicated NFT Image cache manager
    private val nftImageCacheManager = NftImageCacheManager(context)

    // List of predefined tokens that users can select from the dropdown
    private val _internalPredefinedTokens =
            listOf(
                    PredefinedToken(
                            name = "xUSDC",
                            contract = "con_usdc",
                            logoUrl =
                                    "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/ethereum/assets/0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48/logo.png"
                    ),
                    PredefinedToken(
                            name = "Poop Coin",
                            contract = "con_poop_coin",
                            logoUrl =
                                    "https://emojiisland.com/cdn/shop/products/Poop_Emoji_7b204f05-eec6-4496-91b1-351acc03d2c7_large.png"
                    ),
                    PredefinedToken(
                            name = "XTFU Token",
                            contract = "con_xtfu",
                            logoUrl = "https://snakexchange.org/icons/con_xtfu.png"
                    ),
                    PredefinedToken(
                            name = "XIAN Arbitrage",
                            contract = "con_xarb",
                            logoUrl = null // Will use drawable resource instead for local image
                    ),
                    PredefinedToken(
                            name = "Slither Token",
                            contract = "con_slither",
                            logoUrl = "drawable://sss", // Reference to local sss.png drawable
                            symbol = "SSS"
                    ),
                    PredefinedToken(
                            name = "BIGNIG",
                            contract = "con_big_nig_with_a_cig",
                            logoUrl = "drawable://bignigeyes", // Reference to local bignigeyes.png
                            // drawable
                            symbol = "BIGNIG"
                    )
                    // Add more predefined tokens here as needed
                    )

    // Expose predefined tokens to the UI
    private val _predefinedTokens = MutableStateFlow(_internalPredefinedTokens)
    val predefinedTokens: StateFlow<List<PredefinedToken>> = _predefinedTokens.asStateFlow()

    private val _publicKeyFlow =
            MutableStateFlow(
                    walletManager.getActiveWalletPublicKey() ?: ""
            ) // Use a Flow for publicKey
    val publicKey: StateFlow<String> = _publicKeyFlow.asStateFlow()

    // --- State Flows for UI ---
    private val _tokens = MutableStateFlow(walletManager.getOrderedTokenList())
    val tokens: StateFlow<List<String>> = _tokens.asStateFlow()

    private val _tokenInfoMap = MutableStateFlow<Map<String, TokenInfo>>(EMPTY_TOKEN_INFO_MAP)
    val tokenInfoMap: StateFlow<Map<String, TokenInfo>> = _tokenInfoMap.asStateFlow()

    private val _balanceMap = MutableStateFlow<Map<String, Float>>(EMPTY_BALANCE_MAP)
    val balanceMap: StateFlow<Map<String, Float>> = _balanceMap.asStateFlow()

    // Cache first flow for immediate UI updates
    val cachedTokens: StateFlow<List<TokenCacheEntity>> =
            tokenCacheDao
                    .getAllActiveTokens()
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyList()
                    )

    // Cached balances Flow (cache-first) scoped to active public key.
    // Emits last known balances from Room immediately and updates as cache/network writes occur.
    val cachedBalances: StateFlow<Map<String, Float>> =
            _publicKeyFlow
                    .flatMapLatest { key ->
                        if (key.isNotEmpty()) {
                            tokenCacheDao.getActiveTokensWithBalances(key).map { list ->
                                // Only use balances that were fetched at least once (timestamp > 0)
                                list.filter { it.balanceLastUpdated > 0L }.associate {
                                    it.contract to it.cachedBalance
                                }
                            }
                        } else {
                            flowOf(emptyMap())
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyMap()
                    )

    // --- Cached Price State Flows (Cache-First Pattern) ---
    val xianPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("currency")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val xianPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("currency")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    private val _activeWalletName = MutableStateFlow<String?>(null)
    val activeWalletName: StateFlow<String?> = _activeWalletName.asStateFlow()

    // --- POOP Price State Flows (Cache-First) ---
    val poopPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("con_poop_coin")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val poopPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("con_poop_coin")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    // --- XTFU Price State Flows (Cache-First) ---
    val xtfuPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("con_xtfu")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val xtfuPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("con_xtfu")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    // --- XARB Price State Flows (Cache-First) ---
    val xarbPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("con_xarb")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val xarbPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("con_xarb")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    // --- XWT Price State Flows (Cache-First) ---
    val xwtPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("con_xwt")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val xwtPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("con_xwt")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    // --- SSS/Slither Price State Flows (Cache-First) ---
    val slitherPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("con_slither")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val slitherPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("con_slither")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    // --- BIG_NIG Price State Flows (Cache-First) ---
    val bigNigPriceInfo: StateFlow<Pair<Float, Float>?> =
            tokenPriceRepository
                    .getTokenPriceInfo("con_big_nig_with_a_cig")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    val bigNigPrice: StateFlow<Float?> =
            tokenPriceRepository
                    .getTokenPrice("con_big_nig_with_a_cig")
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = null
                    )

    // --- Snapshot (frozen) XIAN price for consistent cross-screen totals (Option A) ---
    private val _xianPriceSnapshot = MutableStateFlow<Float?>(null)
    val xianPriceSnapshot: StateFlow<Float?> = _xianPriceSnapshot.asStateFlow()

    // --- Portfolio Snapshot State ---
    data class PortfolioTokenEntry(
            val contract: String,
            val symbol: String?,
            val balance: Float,
            val usdValue: Float,
            val percent: Float
    )
    data class PortfolioSnapshot(
            val timestamp: Long,
            val xianPrice: Float,
            val totalUsd: Float,
            val tokens: List<PortfolioTokenEntry>
    )
    private val _portfolioSnapshot = MutableStateFlow<PortfolioSnapshot?>(null)
    val portfolioSnapshot: StateFlow<PortfolioSnapshot?> = _portfolioSnapshot.asStateFlow()

    // --- Portfolio 7D Performance (Weighted Sparkline) ---
    data class TokenContribution(
            val contract: String,
            val symbol: String?,
            val weightPercent: Float, // 0..100 at t0
            val finalContributionPercent: Float, // weight * tokenChange(7d) in percentage points
            val token7dChangePercent:
                    Float // raw token % change over the 7d window (final relative change)
    )

    data class PriceSeriesCache(
            val pointsUsd: List<Double>, // length 168, hourly USD prices oldest -> newest
            val epochHourStart: Long, // epochSeconds truncated to hour for points[0]
            val generatedAt: Long, // epochMillis
            val usedFallback: Boolean
    )

    private val _portfolio7dPerformance = MutableStateFlow<List<Float>>(emptyList())
    val portfolio7dPerformance: StateFlow<List<Float>> = _portfolio7dPerformance.asStateFlow()

    private val _portfolioPerfUsedFallback = MutableStateFlow(false)
    val portfolioPerfUsedFallback: StateFlow<Boolean> = _portfolioPerfUsedFallback.asStateFlow()

    private val _tokenContributions = MutableStateFlow<List<TokenContribution>>(emptyList())
    val tokenContributions: StateFlow<List<TokenContribution>> = _tokenContributions.asStateFlow()

    private var lastCompositionWeights: Map<String, Float> = emptyMap()
    private val priceSeriesCache: MutableMap<String, PriceSeriesCache> = mutableMapOf()

    // --- NFT List Flow from Database --- //
    // Use flatMapLatest to switch the underlying Flow when the public key changes
    val nftList: StateFlow<List<NftCacheEntity>> =
            _publicKeyFlow
                    .flatMapLatest { key ->
                        if (key.isNotEmpty()) {
                            Log.d("WalletViewModel", "Subscribing to NFT cache for key: $key")
                            nftCacheDao.getNftsByOwner(key)
                        } else {
                            Log.d(
                                    "WalletViewModel",
                                    "Public key is empty, providing empty NFT list flow."
                            )
                            flowOf(
                                    EMPTY_NFT_CACHE_LIST
                            ) // Return a flow with an empty list if key is empty
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started =
                                    SharingStarted.WhileSubscribed(
                                            5000
                                    ), // Keep subscribed for 5s after last observer
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
    private val _transactionHistory =
            MutableStateFlow<List<LocalTransactionRecord>>(EMPTY_TRANSACTION_HISTORY)
    val transactionHistory: StateFlow<List<LocalTransactionRecord>> =
            _transactionHistory.asStateFlow()

    private val _isTransactionHistoryLoading = MutableStateFlow(false)
    val isTransactionHistoryLoading: StateFlow<Boolean> = _isTransactionHistoryLoading.asStateFlow()

    private val _transactionHistoryError = MutableStateFlow<String?>(null)
    val transactionHistoryError: StateFlow<String?> = _transactionHistoryError.asStateFlow()

    // --- Token-Specific Transaction History States ---
    private val _tokenTransactionHistory =
            MutableStateFlow<List<LocalTransactionRecord>>(EMPTY_TRANSACTION_HISTORY)
    val tokenTransactionHistory: StateFlow<List<LocalTransactionRecord>> =
            _tokenTransactionHistory.asStateFlow()

    private val _isTokenTransactionHistoryLoading = MutableStateFlow(false)
    val isTokenTransactionHistoryLoading: StateFlow<Boolean> =
            _isTokenTransactionHistoryLoading.asStateFlow()

    private val _tokenTransactionHistoryError = MutableStateFlow<String?>(null)
    val tokenTransactionHistoryError: StateFlow<String?> =
            _tokenTransactionHistoryError.asStateFlow()

    // --- End of Transaction History States ---

    // --- Displayed NFT Info --- //
    // This needs adjustment. It should probably react to changes in nftList and preferred contract.
    // For simplicity now, we'll update it within loadData, but a more reactive approach is better.
    private val _displayedNftInfo =
            MutableStateFlow<NftCacheEntity?>(null) // Changed type to NftCacheEntity
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

    // --- Balance Card Background State ---
    private val _selectedCardBackground =
            MutableStateFlow(walletManager.getSelectedCardBackground())
    val selectedCardBackground: StateFlow<String?> = _selectedCardBackground.asStateFlow()

    private val _resolvedXnsAddress = MutableStateFlow<String?>(null)
    val resolvedXnsAddress: StateFlow<String?> = _resolvedXnsAddress.asStateFlow()

    private val _isXnsAddress = MutableStateFlow(false) // True if input is valid XNS and resolved
    val isXnsAddress: StateFlow<Boolean> = _isXnsAddress.asStateFlow()

    private val _isResolvingXns = MutableStateFlow(false)
    val isResolvingXns: StateFlow<Boolean> =
            _isResolvingXns.asStateFlow() // --- Fee Estimation State Flow ---
    private val _estimatedFeeState = MutableStateFlow<FeeEstimationState>(FeeEstimationState.Idle)
    val estimatedFeeState: StateFlow<FeeEstimationState> = _estimatedFeeState.asStateFlow()

    private var currentStampRate: Float? = null // Cache stamp rate

    // --- Chart Model Producer for Vico ---
    val chartModelProducer = ChartEntryModelProducer(EMPTY_CHART_ENTRIES)

    private val _isChartLoading = MutableStateFlow(false)
    val isChartLoading: StateFlow<Boolean> = _isChartLoading.asStateFlow()

    private val _chartError = MutableStateFlow<String?>(null)
    val chartError: StateFlow<String?> = _chartError.asStateFlow()

    private val _chartData = MutableStateFlow<List<Float>>(emptyList())
    val chartData: StateFlow<List<Float>> = _chartData.asStateFlow()
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
        _activeWalletName.value =
                walletManager.getActiveWalletName() // Initialize active wallet name

        // Apply cached balances to UI immediately (cache-first) and keep them in sync with DB
        viewModelScope.launch {
            cachedBalances.collect { cacheMap ->
                try {
                    if (cacheMap.isNotEmpty()) {
                        val currentTokens = _tokens.value.toSet()
                        val filtered = cacheMap.filterKeys { it in currentTokens }
                        val previous = _balanceMap.value
                        if (filtered.any { (k, v) -> previous[k] != v }) {
                            val merged = previous.toMutableMap()
                            merged.putAll(filtered)
                            _balanceMap.value = merged
                            Log.d(
                                    "WalletViewModel",
                                    "Applied cached balances to UI: ${filtered.size} tokens"
                            )
                        }
                    } else {
                        Log.d("WalletViewModel", "No cached balances with timestamps to apply")
                    }
                } catch (e: Exception) {
                    Log.e("WalletViewModel", "Error applying cached balances", e)
                }
            }
        }

        // Cargar información adicional de los tokens predefinidos (logos, etc)
        loadPredefinedTokensInfo()

        // Precargar tokens predefinidos en cache y luego cargar datos
        val preloadJob = preloadPredefinedTokensToCache()

        // Wait for preload to complete, then load data
        viewModelScope.launch {
            preloadJob.join()
            loadDataIfNotLoaded()
        }

        // Observe the active wallet public key flow for CHANGES
        viewModelScope.launch {
            var isInitialValue = true // Flag to skip reaction to the very first emission if needed
            walletManager.activeWalletPublicKeyFlow.collect { activeKey ->
                Log.d(
                        "WalletViewModel",
                        "Observed active key change: $activeKey (isInitialValue: $isInitialValue)"
                )

                // Only react to changes *after* the initial state is processed
                if (!isInitialValue) {
                    val newKey = activeKey ?: ""
                    val currentKey = _publicKeyFlow.value
                    if (newKey != currentKey) {
                        _publicKeyFlow.value = newKey // Update the ViewModel's public key flow
                        _activeWalletName.value =
                                walletManager.getActiveWalletName() // Update active wallet name
                        hasLoadedInitialData =
                                false // Reset flag to force reload for the new wallet
                        _ownedXnsNames.value = EMPTY_XNS_NAME_LIST // Clear XNS names
                        _xnsNameExpirations.value = EMPTY_XNS_EXPIRATIONS // Clear expirations
                        _transactionHistory.value =
                                EMPTY_TRANSACTION_HISTORY // Clear transaction history
                        _transactionHistoryError.value = null // Clear errors
                        _tokenTransactionHistory.value =
                                EMPTY_TRANSACTION_HISTORY // Clear token transaction history
                        _tokenTransactionHistoryError.value = null // Clear token transaction errors
                        _balanceMap.value =
                                EMPTY_BALANCE_MAP // Clear balance map to prevent stale total
                        // balance
                        // No need to clear _nftList, the flatMapLatest will switch the source Flow
                        _displayedNftInfo.value = null // Clear displayed NFT
                        if (newKey.isNotEmpty()) {
                            preloadPredefinedTokensToCache() // Preload predefined tokens for new
                            // wallet
                            _tokens.value =
                                    walletManager
                                            .getOrderedTokenList() // Reset token list from manager
                            // for new wallet
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
                            _isTransactionHistoryLoading.value = false
                            _tokenTransactionHistory.value = EMPTY_TRANSACTION_HISTORY
                            _isTokenTransactionHistoryLoading.value =
                                    false // Clear chart data as well
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

        // Preload token logos on startup in background
        preloadTokenLogosFromCache()
        loadTransactionHistory() // Load initial transaction history        // Start periodic
        // connectivity check
        startConnectivityChecks()

        // Start periodic price refresh for all supported tokens
        val supportedTokens =
                listOf(
                        "currency",
                        "con_poop_coin",
                        "con_xtfu",
                        "con_xarb",
                        "con_xwt",
                        "con_slither",
                        "con_big_nig_with_a_cig"
                )
        tokenPriceRepository.startPeriodicRefresh(supportedTokens)

        // Capture first non-null XIAN price as snapshot (Option A behavior centralized here)
        viewModelScope.launch {
            xianPrice.collect { px ->
                if (px != null && _xianPriceSnapshot.value == null) {
                    _xianPriceSnapshot.value = px
                    Log.d("WalletViewModel", "Captured XIAN price snapshot: $px")
                }
            }
        }

        // Build portfolio snapshot whenever relevant flows change.
        viewModelScope.launch {
            combine(
                            balanceMap,
                            tokens,
                            poopPrice,
                            xtfuPrice,
                            xarbPrice,
                            xwtPrice,
                            slitherPrice,
                            bigNigPrice,
                            xianPriceSnapshot,
                            tokenInfoMap
                    ) { values: Array<Any?> ->
                @Suppress("UNCHECKED_CAST") val balanceMapVal = values[0] as Map<String, Float>
                @Suppress("UNCHECKED_CAST") val tokenList = values[1] as List<String>
                val poopP = values[2] as Float?
                val xtfuP = values[3] as Float?
                val xarbP = values[4] as Float?
                val xwtP = values[5] as Float?
                val slitherP = values[6] as Float?
                val bigNigP = values[7] as Float?
                val xianSnap = values[8] as Float?
                @Suppress("UNCHECKED_CAST") val infoMap = values[9] as Map<String, TokenInfo>

                if (xianSnap == null) return@combine null

                val priceInXianByContract =
                        mapOf(
                                "con_poop_coin" to poopP,
                                "con_xtfu" to xtfuP,
                                "con_xarb" to xarbP,
                                "con_xwt" to xwtP,
                                "con_slither" to slitherP,
                                "con_big_nig_with_a_cig" to bigNigP
                        )

                val entries =
                        tokenList
                                .map { contract ->
                                    val balance = balanceMapVal[contract] ?: 0f
                                    val usdValue =
                                            when (contract) {
                                                "currency" -> balance * xianSnap
                                                "con_usdc" -> balance
                                                else ->
                                                        priceInXianByContract[contract]?.let {
                                                            balance * it * xianSnap
                                                        }
                                                                ?: 0f
                                            }
                                    PortfolioTokenEntry(
                                            contract = contract,
                                            symbol = infoMap[contract]?.symbol,
                                            balance = balance,
                                            usdValue = usdValue,
                                            percent = 0f
                                    )
                                }
                                .toMutableList()

                val totalUsd = entries.sumOf { it.usdValue.toDouble() }.toFloat()
                if (totalUsd > 0f && entries.isNotEmpty()) {
                    var runningSum = 0f
                    val lastIndex = entries.lastIndex
                    entries.forEachIndexed { index, e ->
                        val pct =
                                if (index == lastIndex) {
                                    (100f - runningSum).coerceIn(0f, 100f)
                                } else {
                                    val raw = (e.usdValue / totalUsd) * 100f
                                    val rounded = (raw * 10f).toInt() / 10f
                                    runningSum += rounded
                                    rounded
                                }
                        entries[index] = e.copy(percent = pct)
                    }
                }

                PortfolioSnapshot(
                        timestamp = System.currentTimeMillis(),
                        xianPrice = xianSnap,
                        totalUsd = totalUsd,
                        tokens = entries
                )
            }
                    .collect { snap -> _portfolioSnapshot.value = snap }
        }

        // Observe snapshot changes to recompute 7D performance when composition shifts > 1%
        viewModelScope.launch {
            portfolioSnapshot.collect { snap ->
                snap?.let {
                    if (shouldRecomputePortfolioPerf(it)) {
                        computePortfolio7dPerformance(it)
                    }
                }
            }
        }
    }

    // --- Public Functions for UI Interaction ---
    /**
     * Loads historical price data for the given token contract and updates the chart. Fetches swap
     * events from GraphQL and processes them into chart data.
     */
    fun loadHistoricalData(tokenContract: String, timePeriod: String = "1D") {
        viewModelScope.launch {
            _isChartLoading.value = true
            _chartError.value = null
            _chartNormalizationType.value = null // Reset normalization state
            _chartYAxisRange.value = null // Reset Y-axis range
            _chartYAxisOffset.value = null // Reset Y-axis offset
            Log.d(
                    "WalletViewModel",
                    "Loading historical data for chart: $tokenContract ($timePeriod)"
            )

            try {
                // Use the new helper method to get historical data for the specific time period
                val chartEntries = getHistoricalDataForPeriod(tokenContract, timePeriod)

                if (chartEntries.isNotEmpty()) {
                    chartModelProducer.setEntries(chartEntries)
                    // Extract y values for SimpleCryptoChart
                    val chartDataList = chartEntries.map { it.y }
                    _chartData.value = chartDataList
                    Log.d(
                            "WalletViewModel",
                            "Chart updated with ${chartEntries.size} data points for $timePeriod period"
                    )
                    Log.d(
                            "WalletViewModel",
                            "Chart data values: ${chartDataList.take(5)}..."
                    ) // Log first 5 values
                } else {
                    _chartError.value = "No price data available for $tokenContract ($timePeriod)"
                    chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                    _chartData.value = emptyList()
                    _chartNormalizationType.value = null
                    _chartYAxisRange.value = null
                    _chartYAxisOffset.value = null
                }
            } catch (e: Exception) {
                Log.e(
                        "WalletViewModel",
                        "Error loading historical chart data for $tokenContract ($timePeriod)",
                        e
                )
                _chartError.value = "Failed to load chart data for $timePeriod: ${e.message}"
                _chartData.value = emptyList()
                chartModelProducer.setEntries(EMPTY_CHART_ENTRIES)
                _chartNormalizationType.value = null
                _chartYAxisRange.value = null
                _chartYAxisOffset.value = null
            } finally {
                _isChartLoading.value = false
            }
        }
    }

    /** Processes swap events into chart data points */
    private fun processSwapEventsToChartData(
            swapEvents: List<SwapEvent>,
            tokenContract: String,
            tokenPair: PairInfo
    ): List<FloatEntry> {
        try {
            // Sort events by timestamp (oldest first for processing, will be reversed for display)
            val sortedEvents: List<SwapEvent> =
                    swapEvents.sortedBy { event ->
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
            }

            // Convert candles to chart entries with volume-weighted average price (VWAP)
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
                    val finalPrice =
                            if (totalVolume > 0) {
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
            }

            // Calcular estadísticas para optimizar la escala
            if (priceList.isNotEmpty()) {
                val minPrice = priceList.minOrNull() ?: 0.0
                val maxPrice = priceList.maxOrNull() ?: 0.0
                val avgPrice = priceList.average()
                val priceRange = maxPrice - minPrice
                val relativeVariation = if (avgPrice > 0) (priceRange / avgPrice) else 0.0

                Log.d(
                        "WalletViewModel",
                        "Price stats - Min: $minPrice, Max: $maxPrice, Avg: $avgPrice, Range: $priceRange, RelVar: ${relativeVariation * 100}%"
                )
                // Usar normalización agresiva para pequeñas variaciones
                val hasSmallVariation = relativeVariation < 0.05 && priceRange > 0 // 5% threshold
                // Calcular padding para mejor visualización (5% arriba y abajo)
                val paddingPercent = 0.05
                val padding = priceRange * paddingPercent
                val displayMin =
                        (minPrice - padding).coerceAtLeast(0.0) // No permitir valores negativos
                val displayMax = maxPrice + padding
                // Set Y-axis range for the chart
                _chartYAxisRange.value = Pair(displayMin.toFloat(), displayMax.toFloat())
                // Check if we need to use offset mapping for better Y-axis scaling
                // If price range is small relative to minimum price, we'll offset the data
                val shouldUseOffset = minPrice > 0 && relativeVariation < 0.5 && minPrice > 0.001
                val offset = if (shouldUseOffset) displayMin else 0.0

                // Store the offset for the chart axis formatter
                _chartYAxisOffset.value = if (shouldUseOffset) offset.toFloat() else null

                // Segunda pasada: crear entradas del gráfico con precios normalizados si es
                // necesario
                // Reverse the order so the most recent data appears first (right side of chart)
                priceList.reversed().forEachIndexed { index, price ->
                    // Apply offset if needed to center the chart around the actual price range
                    val adjustedPrice = if (shouldUseOffset) (price - offset) else price
                    chartEntries.add(entryOf(xIndex, adjustedPrice.toFloat()))
                    xIndex += 1f
                }

                // Establecer información de escala para el eje Y - Siempre mostrar el rango
                val scaleInfo =
                        if (shouldUseOffset) {
                            "Low: ${"%.6f".format(Locale.US, displayMin)} - High: ${"%.6f".format(Locale.US, displayMax)} (offset applied)"
                        } else {
                            "Low: ${"%.6f".format(Locale.US, displayMin)} - High: ${"%.6f".format(Locale.US, displayMax)}"
                        }
                _chartNormalizationType.value = scaleInfo

                if (shouldUseOffset) {
                    Log.d(
                            "WalletViewModel",
                            "Using offset mapping to center chart around price range: $scaleInfo"
                    )
                } else if (hasSmallVariation) {
                    Log.d(
                            "WalletViewModel",
                            "Using real prices with enhanced scale for small variation (${relativeVariation * 100}%): $scaleInfo"
                    )
                } else {
                    Log.d(
                            "WalletViewModel",
                            "Using real prices with normal scale (${relativeVariation * 100}% variation): $scaleInfo"
                    )
                }
            }

            Log.d(
                    "WalletViewModel",
                    "Created ${chartEntries.size} chart entries (${currentTimeframe.displayName} candles) from ${sortedEvents.size} swap events - showing most recent first"
            )
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

    fun setSelectedCardBackground(backgroundName: String?) {
        _selectedCardBackground.value = backgroundName
        walletManager.setSelectedCardBackground(backgroundName)
        Log.d("WalletViewModel", "Card background set to: $backgroundName")
    }

    fun refreshData() {
        Log.d("WalletViewModel", "Manual refresh triggered.")
        // Reset token list from manager in case it changed
        _tokens.value = walletManager.getOrderedTokenList()
        // Force load data
        loadData(force = true)
        loadTransactionHistory(force = true) // Refresh transaction history

        // Force refresh all token prices
        viewModelScope.launch {
            val supportedTokens =
                    listOf(
                            "currency",
                            "con_poop_coin",
                            "con_xtfu",
                            "con_xarb",
                            "con_xwt",
                            "con_slither",
                            "con_big_nig_with_a_cig"
                    )
            tokenPriceRepository.refreshMultiplePrices(supportedTokens)
        }
    }

    fun refreshActiveWalletName() {
        Log.d("WalletViewModel", "Refreshing active wallet name.")
        _activeWalletName.value = walletManager.getActiveWalletName()
    }

    // Updated to accept NftCacheEntity
    fun setPreferredNft(nft: NftCacheEntity) {
        _displayedNftInfo.value = nft
        walletManager.setPreferredNftContract(nft.contractAddress)
        Log.d("WalletViewModel", "Preferred NFT set to: ${nft.contractAddress}")
    }

    fun addTokenAndRefresh(
            contract: String,
            onResult: ((net.xian.xianwalletapp.wallet.TokenAddResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            // 1. Verificar existencia del contrato antes de intentar añadirlo
            val exists =
                    try {
                        networkService.contractExists(contract)
                    } catch (e: Exception) {
                        Log.e(
                                "WalletViewModel",
                                "Error checking contract existence for $contract",
                                e
                        )
                        false
                    }
            if (!exists) {
                Log.w("WalletViewModel", "Attempt to add non-existent contract: $contract")
                onResult?.invoke(net.xian.xianwalletapp.wallet.TokenAddResult.INVALID_CONTRACT)
                return@launch
            }

            val result = walletManager.addToken(contract)
            when (result) {
                net.xian.xianwalletapp.wallet.TokenAddResult.SUCCESS -> {
                    Log.d("WalletViewModel", "Token $contract added to WalletManager.")
                    // Immediately update the token list for instant UI feedback
                    _tokens.value = walletManager.getOrderedTokenList()
                    // Load from cache first, then refresh in background
                    loadTokenFromCacheFirst(contract)
                }
                net.xian.xianwalletapp.wallet.TokenAddResult.ALREADY_EXISTS -> {
                    Log.i("WalletViewModel", "Token $contract already exists in wallet.")
                    // Token already exists, no need to refresh, but this is not an error
                }
                net.xian.xianwalletapp.wallet.TokenAddResult.INVALID_CONTRACT -> {
                    Log.w("WalletViewModel", "Invalid contract address: $contract")
                }
                net.xian.xianwalletapp.wallet.TokenAddResult.NO_ACTIVE_WALLET -> {
                    Log.w("WalletViewModel", "No active wallet to add token to")
                }
                net.xian.xianwalletapp.wallet.TokenAddResult.FAILED -> {
                    Log.w("WalletViewModel", "Failed to add token $contract via WalletManager.")
                }
            }
            onResult?.invoke(result)
        }
    }

    fun removeToken(contract: String) {
        viewModelScope.launch {
            if (contract == "currency") return@launch // Prevent removing base currency

            if (walletManager.removeToken(contract)) {
                Log.d("WalletViewModel", "Token $contract removed from WalletManager.")
                // Update the internal list and trigger refresh
                _tokens.value = walletManager.getOrderedTokenList()
                // Refresh only token-related data (not NFTs)
                refreshTokenDataOnly()
            } else {
                Log.w("WalletViewModel", "Failed to remove token $contract via WalletManager.")
            }
        }
    }

    /** Reorder tokens with custom order and save the preference */
    fun reorderTokens(newOrder: List<String>) {
        viewModelScope.launch {
            if (walletManager.saveTokenOrder(newOrder)) {
                Log.d("WalletViewModel", "Token order saved successfully")
                // Update the UI with the new order
                _tokens.value = newOrder
            } else {
                Log.w("WalletViewModel", "Failed to save token order")
                // Optionally show error message to user
            }
        }
    }

    /**
     * Checks if the input string could be an XNS name and attempts to resolve it. Updates
     * isXnsAddress and resolvedXnsAddress states. Uses basic validation similar to the web wallet.
     */
    fun checkAndResolveXns(recipientInput: String) {
        // Reset state first
        _isXnsAddress.value = false
        _resolvedXnsAddress.value = null
        _isResolvingXns.value = false // Ensure loading is false initially

        // Basic validation (similar to web wallet)
        if (recipientInput.isBlank() ||
                        recipientInput.length < 3 ||
                        recipientInput.length > 64 ||
                        !recipientInput.matches(Regex("^[a-zA-Z0-9]+$"))
        ) {
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
    private val _transactionResult =
            MutableStateFlow<TransactionResult?>(null) // Correct initialization with null
    val transactionResult: StateFlow<TransactionResult?> = _transactionResult.asStateFlow()

    private val _isSendingTransaction = MutableStateFlow(false)
    val isSendingTransaction: StateFlow<Boolean> = _isSendingTransaction.asStateFlow()

    /**
     * Sends a token transfer transaction. Handles nonce fetching, signing, and broadcasting.
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
            val result =
                    networkService.sendTransaction(
                            contract = contract,
                            method = "transfer",
                            kwargs =
                                    JSONObject().apply { // Use imported JSONObject
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
            val errorResult =
                    TransactionResult(
                            success = false,
                            errors = e.message ?: "Unknown error during transaction"
                    ) // Use imported TransactionResult
            _transactionResult.value = errorResult
            errorResult // Return error result
        } finally {
            _isSendingTransaction.value = false
        }
    }

    /**
     * Pay the AI analysis fee in XWT after a successful analysis. This uses the unlocked private
     * key if available (no prompt). If the wallet is locked, it returns an error.
     *
     * @param recipientAddress The fixed recipient for the AI fee.
     * @param amount The XWT amount to send as a decimal string (human-readable).
     * @param stampLimit Optional stamp (gas) limit.
     */
    suspend fun payXwtFee(
            recipientAddress: String,
            amount: String,
            stampLimit: Int = 500000
    ): TransactionResult {
        _isSendingTransaction.value = true
        _transactionResult.value = null
        return try {
            val privateKey = walletManager.getUnlockedPrivateKey()
            if (privateKey == null) {
                val err =
                        TransactionResult(
                                success = false,
                                errors = "Wallet is locked. Unlock the wallet to process AI fee."
                        )
                _transactionResult.value = err
                err
            } else {
                val result =
                        networkService.sendTransaction(
                                contract = "con_xwt",
                                method = "transfer",
                                kwargs =
                                        JSONObject().apply {
                                            put("to", recipientAddress)
                                            put("amount", BigDecimal(amount))
                                        },
                                privateKey = privateKey,
                                stampLimit = stampLimit
                        )
                _transactionResult.value = result
                result
            }
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error paying XWT fee", e)
            val errorResult =
                    TransactionResult(
                            success = false,
                            errors = e.message ?: "Unknown error during XWT fee payment"
                    )
            _transactionResult.value = errorResult
            errorResult
        } finally {
            _isSendingTransaction.value = false
        }
    }

    /** Clears the transaction result state, e.g., after navigating away or showing a message. */
    fun clearTransactionResult() {
        _transactionResult.value = null
    }

    /**
     * Resets the fee estimation state back to Idle. Call this after the UI has consumed the fee
     * estimation result.
     */
    fun clearFeeEstimationState() {
        _estimatedFeeState.value = FeeEstimationState.Idle
    }

    /** Requests the estimation of transaction fees (stamps). Updates the estimatedFeeState flow. */
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
            val privateKey =
                    walletManager.getUnlockedPrivateKey()
                            ?: run {
                                Log.e(
                                        "WalletViewModel",
                                        "requestFeeEstimation called but wallet is locked. This shouldn't happen with correct UI flow."
                                )
                                _estimatedFeeState.value =
                                        FeeEstimationState
                                                .Failure // Treat as failure if called incorrectly
                                return@launch
                            }

            try {
                // Construct kwargs
                val kwargs =
                        JSONObject().apply {
                            put("to", recipientAddress)
                            put("amount", BigDecimal(amount)) // Use BigDecimal for accuracy
                        }

                // Estimate stamps
                val estimatedStamps =
                        networkService.estimateTransactionFee(
                                contract = contract,
                                method = "transfer",
                                kwargs = kwargs,
                                publicKey = senderPublicKey,
                                privateKey = privateKey
                        )

                if (estimatedStamps == null || estimatedStamps <= 0) {
                    Log.e(
                            "WalletViewModel",
                            "Fee estimation failed or returned invalid value: $estimatedStamps"
                    )
                    _estimatedFeeState.value = FeeEstimationState.Failure
                    return@launch
                }

                // Get stamp rate (use cached value if available)
                val rate =
                        currentStampRate
                                ?: try {
                                    networkService.getStampRate().also { currentStampRate = it }
                                } catch (e: Exception) {
                                    Log.e(
                                            "WalletViewModel",
                                            "Failed to get stamp rate for fee calculation",
                                            e
                                    )
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
                val feeForDisplay =
                        feeInXianInternal.divide(
                                BigDecimal(10000)
                        ) // Changed divisor back to 10,000

                // Format the fee for display (e.g., with 4 decimal places)
                val numberFormat =
                        NumberFormat.getNumberInstance(Locale.US).apply {
                            maximumFractionDigits = 4 // Adjust precision as needed
                            minimumFractionDigits = 2 // Show at least 2 decimals
                        }
                val formattedFee = "${numberFormat.format(feeForDisplay)} XIAN"

                Log.d(
                        "WalletViewModel",
                        "Fee estimation success: Stamps=$estimatedStamps, Rate=$rate, Fee=$formattedFee"
                )
                _estimatedFeeState.value = FeeEstimationState.Success(formattedFee)
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error during fee estimation request", e)
                _estimatedFeeState.value = FeeEstimationState.Failure
            }
        }
    }
    /** Changes the chart timeframe and reloads chart data */
    fun setChartTimeframe(timeframe: ChartTimeframe, tokenContract: String? = null) {
        if (_chartTimeframe.value != timeframe) {
            _chartTimeframe.value = timeframe
            Log.d("WalletViewModel", "Chart timeframe changed to: ${timeframe.displayName}")

            // Reload chart data with new timeframe if we have a token contract
            tokenContract?.let { contract -> loadHistoricalData(contract) }
        }
    }

    // --- Private Data Loading Logic ---
    // --- Private Data Loading Logic ---

    /** Intenta cargar información adicional del token como logos para los tokens predefinidos */
    fun loadPredefinedTokensInfo() {
        viewModelScope.launch {
            try {
                // Crear una nueva lista mutable para los tokens actualizados
                val updatedTokens =
                        _internalPredefinedTokens.map { token -> // Corrected => to ->
                            if (token.logoUrl == null) {
                                // Si el token no tiene logo, intentar obtener información del
                                // servicio
                                try {
                                    val tokenInfo = networkService.getTokenInfo(token.contract)
                                    // Crear un nuevo token con la información actualizada
                                    token.copy(logoUrl = tokenInfo.logoUrl)
                                } catch (e: Exception) {
                                    Log.e(
                                            "WalletViewModel",
                                            "Error obteniendo info para token ${token.contract}",
                                            e
                                    )
                                    token // Mantener el token original si hay error
                                }
                            } else {
                                token // Mantener el token original si ya tiene logo
                            }
                        }

                // Actualizar la lista de tokens predefinidos
                _predefinedTokens.value = updatedTokens
                Log.d(
                        "WalletViewModel",
                        "Lista de tokens predefinidos actualizada con información adicional"
                )

                // Cache token logos for predefined tokens
                cacheTokenLogos(updatedTokens)
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error al actualizar tokens predefinidos", e)
            }
        }
    }

    private fun loadDataIfNotLoaded() {
        if (!hasLoadedInitialData && _publicKeyFlow.value.isNotEmpty()) {
            Log.d("WalletViewModel", "Initial data load triggered for key: ${_publicKeyFlow.value}")
            loadFromCacheFirst()
        } else {
            Log.d(
                    "WalletViewModel",
                    "Skipping initial data load. Already loaded or public key empty."
            )
            _isLoading.value = false
            _isNftLoading.value = false
        }
    }

    /**
     * Cache-first data loading strategy
     * 1. Load immediately from cache
     * 2. Update UI with cached data
     * 3. Sync with network in background
     */
    private fun loadFromCacheFirst() {
        val currentPublicKey = _publicKeyFlow.value
        if (currentPublicKey.isEmpty()) {
            Log.w("WalletViewModel", "Skipping cache-first load, public key is empty.")
            _isLoading.value = false
            _isNftLoading.value = false
            return
        }

        viewModelScope.launch {
            Log.d("WalletViewModel", "Starting cache-first load for key: $currentPublicKey")

            // Phase 1: Load from cache immediately
            loadFromCache()

            // Phase 2: Background network sync
            syncWithNetwork()

            hasLoadedInitialData = true
        }
    }

    /** Load data immediately from local cache */
    private suspend fun loadFromCache() {
        Log.d("WalletViewModel", "Loading data from cache...")

        try {
            val currentTokens = walletManager.getOrderedTokenList()
            _tokens.value = currentTokens

            val cachedTokenInfoMap = mutableMapOf<String, TokenInfo>()
            val cachedBalanceMap = mutableMapOf<String, Float>()

            // Load cached token info and balances
            val currentPublicKey = _publicKeyFlow.value
            Log.d("WalletViewModel", "Cache load - current tokens: $currentTokens")
            Log.d("WalletViewModel", "Cache load - current public key: $currentPublicKey")
            currentTokens.forEach { contract ->
                try {
                    val cachedToken = tokenCacheDao.getTokenWithBalance(contract, currentPublicKey)
                    Log.d(
                            "WalletViewModel",
                            "Cache lookup for $contract: ${if (cachedToken != null) "FOUND" else "NOT FOUND"}"
                    )
                    if (cachedToken != null) {
                        Log.d(
                                "WalletViewModel",
                                "Cache entry details for $contract: balance=${cachedToken.cachedBalance}, lastUpdated=${cachedToken.balanceLastUpdated}, owner=${cachedToken.ownerPublicKey}"
                        )
                        val tokenInfo =
                                TokenInfo(
                                        name = cachedToken.name,
                                        symbol = cachedToken.symbol,
                                        contract = cachedToken.contract,
                                        logoUrl = cachedToken.logoUrl
                                )
                        cachedTokenInfoMap[contract] = tokenInfo

                        // Load cached balance if available (including 0.0 balances with valid
                        // timestamps)
                        if (cachedToken.balanceLastUpdated > 0L) {
                            cachedBalanceMap[contract] = cachedToken.cachedBalance
                            Log.d(
                                    "WalletViewModel",
                                    "Loaded cached balance for $contract: ${cachedToken.cachedBalance}"
                            )
                        } else if (cachedToken.cachedBalance > 0f) {
                            // Also load non-zero balances even if timestamp is 0 (for backward
                            // compatibility)
                            cachedBalanceMap[contract] = cachedToken.cachedBalance
                            Log.d(
                                    "WalletViewModel",
                                    "Loaded cached balance (no timestamp) for $contract: ${cachedToken.cachedBalance}"
                            )
                        } else {
                            Log.d(
                                    "WalletViewModel",
                                    "No cached balance for $contract (balance=${cachedToken.cachedBalance}, lastUpdated=${cachedToken.balanceLastUpdated})"
                            )
                        }

                        Log.d(
                                "WalletViewModel",
                                "Loaded cached info for $contract: ${cachedToken.name}"
                        )
                    } else {
                        // Fall back to predefined tokens if not in cache
                        val predefinedToken =
                                _predefinedTokens.value.find { it.contract == contract }
                        if (predefinedToken != null) {
                            val tokenInfo =
                                    TokenInfo(
                                            name = predefinedToken.name,
                                            symbol = predefinedToken.symbol
                                                            ?: predefinedToken
                                                                    .contract
                                                                    .takeLast(4)
                                                                    .uppercase(),
                                            contract = predefinedToken.contract,
                                            logoUrl = predefinedToken.logoUrl
                                    )
                            cachedTokenInfoMap[contract] = tokenInfo
                            Log.d(
                                    "WalletViewModel",
                                    "Using predefined info for $contract: ${predefinedToken.name}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WalletViewModel", "Error loading cached data for $contract", e)
                }
            }

            // Update UI with cached data (including cached balances)
            _tokenInfoMap.value = cachedTokenInfoMap
            _balanceMap.value = cachedBalanceMap

            Log.d(
                    "WalletViewModel",
                    "Cache load complete - loaded ${cachedTokenInfoMap.size} tokens from cache"
            )
            Log.d("WalletViewModel", "Cached balances loaded: $cachedBalanceMap")
            Log.d("WalletViewModel", "UI balanceMap updated with ${cachedBalanceMap.size} entries")
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error during cache load", e)
        }
    }

    /** Sync with network in background and update cache */
    private suspend fun syncWithNetwork() {
        val currentPublicKey = _publicKeyFlow.value
        if (currentPublicKey.isEmpty()) return

        Log.d("WalletViewModel", "Starting background network sync for key: $currentPublicKey")

        _isLoading.value = true
        _isNftLoading.value = true

        try {
            // Check connectivity
            _isCheckingConnection.value = true
            _isNodeConnected.value = networkService.checkNodeConnectivity()
            _isCheckingConnection.value = false

            if (!_isNodeConnected.value) {
                Log.w("WalletViewModel", "Network not available, skipping sync")
                _isLoading.value = false
                _isNftLoading.value = false
                return
            }

            val currentTokens = _tokens.value
            val networkTokenInfoMap = mutableMapOf<String, TokenInfo>()
            val networkBalanceMap = mutableMapOf<String, Float>()

            // Fetch fresh data from network
            currentTokens.forEach { contract ->
                try {
                    // Get token info from network
                    val predefinedToken = _predefinedTokens.value.find { it.contract == contract }
                    val tokenInfo =
                            if (predefinedToken != null && predefinedToken.logoUrl != null) {
                                TokenInfo(
                                        name = predefinedToken.name,
                                        symbol = predefinedToken.symbol
                                                        ?: predefinedToken
                                                                .contract
                                                                .takeLast(4)
                                                                .uppercase(),
                                        contract = predefinedToken.contract,
                                        logoUrl = predefinedToken.logoUrl
                                )
                            } else {
                                networkService.getTokenInfo(contract)
                            }

                    networkTokenInfoMap[contract] = tokenInfo

                    // Get balance from network
                    val networkBalance = networkService.getTokenBalance(contract, currentPublicKey)
                    networkBalanceMap[contract] = networkBalance
                    Log.d("WalletViewModel", "Network balance for $contract: $networkBalance")

                    // Compare with cached balance and update cache if different
                    val cachedBalance = tokenCacheDao.getCachedBalance(contract, currentPublicKey)
                    val shouldUpdateCache = cachedBalance == null || cachedBalance != networkBalance
                    Log.d(
                            "WalletViewModel",
                            "Cache comparison for $contract: cached=$cachedBalance, network=$networkBalance, shouldUpdate=$shouldUpdateCache"
                    )

                    if (shouldUpdateCache) {
                        val currentTime = System.currentTimeMillis()

                        // Ensure token exists with correct ownerPublicKey before updating balance
                        val existingToken =
                                tokenCacheDao.getTokenWithBalance(contract, currentPublicKey)
                        if (existingToken == null) {
                            // Token doesn't exist for this owner, create it first
                            val newTokenEntity =
                                    TokenCacheEntity(
                                            contract = contract,
                                            name = networkTokenInfoMap[contract]?.name ?: contract,
                                            symbol = networkTokenInfoMap[contract]?.symbol
                                                            ?: contract.takeLast(4).uppercase(),
                                            decimals = 8,
                                            logoUrl = networkTokenInfoMap[contract]?.logoUrl,
                                            isLogoCached = false,
                                            lastUpdated = currentTime,
                                            isActive = true,
                                            cachedBalance = networkBalance,
                                            balanceLastUpdated = currentTime,
                                            ownerPublicKey = currentPublicKey
                                    )
                            tokenCacheDao.insertToken(newTokenEntity)
                            Log.d(
                                    "WalletViewModel",
                                    "Created new token cache entry for $contract with balance $networkBalance"
                            )
                        } else {
                            // Token exists, update balance
                            tokenCacheDao.updateTokenBalance(
                                    contract,
                                    currentPublicKey,
                                    networkBalance,
                                    currentTime
                            )
                            Log.d(
                                    "WalletViewModel",
                                    "Updated cache for $contract: $cachedBalance -> $networkBalance"
                            )

                            // Verify the update was successful
                            val verifyUpdated =
                                    tokenCacheDao.getCachedBalance(contract, currentPublicKey)
                            Log.d(
                                    "WalletViewModel",
                                    "Verification after update for $contract: $verifyUpdated"
                            )
                        }
                    } else {
                        Log.d(
                                "WalletViewModel",
                                "Cache unchanged for $contract: balance $networkBalance"
                        )
                    }

                    Log.d("WalletViewModel", "Network sync: $contract - balance: $networkBalance")
                } catch (e: Exception) {
                    Log.e("WalletViewModel", "Error fetching network data for token $contract", e)
                }
            }

            // Update cache with fresh network data
            updateCacheWithNetworkData(networkTokenInfoMap)

            // Update UI with network data only if values changed
            _tokenInfoMap.value = networkTokenInfoMap

            // For balances, only update UI if values actually changed from cache
            val currentBalanceMap = _balanceMap.value.toMutableMap()
            var balanceMapChanged = false

            networkBalanceMap.forEach { (contract, networkBalance) ->
                val currentBalance = currentBalanceMap[contract]
                if (currentBalance == null || currentBalance != networkBalance) {
                    currentBalanceMap[contract] = networkBalance
                    balanceMapChanged = true
                    Log.d(
                            "WalletViewModel",
                            "UI updated for $contract: $currentBalance -> $networkBalance"
                    )
                }
            }

            // Only update UI if balances actually changed
            if (balanceMapChanged) {
                _balanceMap.value = currentBalanceMap
                Log.d("WalletViewModel", "Balance map updated in UI")
            } else {
                Log.d("WalletViewModel", "No balance changes, UI unchanged")
            }

            // Cache token logos after network sync
            cacheTokenLogosFromInfoMap(networkTokenInfoMap)

            // Note: Price fetching is now handled by TokenPriceRepository in background
            // The UI will automatically update when prices are available from cache or network

            // --- Fetch NFTs and XNS Names & Expirations --- //
            var fetchedNetworkNfts: List<NftInfo> = emptyList()
            var validXnsNames: List<String> = emptyList()
            var xnsExpirationsMap: Map<String, Long?> = emptyMap()

            // Fetch NFTs from Network
            try {
                fetchedNetworkNfts = networkService.getNfts(currentPublicKey)
                Log.d(
                        "WalletViewModel",
                        "Fetched ${fetchedNetworkNfts.size} NFTs from network for $currentPublicKey"
                )

                // --- Update Room Database --- //
                val nftEntities =
                        fetchedNetworkNfts.map { nftInfo ->
                            NftCacheEntity(
                                    contractAddress = nftInfo.contractAddress,
                                    ownerPublicKey =
                                            currentPublicKey, // Associate with current wallet
                                    name = nftInfo.name,
                                    description = nftInfo.description,
                                    imageUrl = nftInfo.imageUrl,
                                    viewUrl = nftInfo.viewUrl
                            )
                        }
                // Insert or update fetched NFTs
                nftCacheDao.insertOrUpdateNfts(nftEntities)
                Log.d(
                        "WalletViewModel",
                        "Inserted/Updated ${nftEntities.size} NFTs into cache for $currentPublicKey"
                )

                // Delete NFTs from cache that are no longer associated with the owner
                val currentNftContracts = fetchedNetworkNfts.map { it.contractAddress }
                nftCacheDao.deleteOrphanedNfts(currentPublicKey, currentNftContracts)
                Log.d("WalletViewModel", "Deleted orphaned NFTs from cache for $currentPublicKey")
                // --- Room Update Complete ---

            } catch (e: Exception) {
                Log.e(
                        "WalletViewModel",
                        "Error fetching NFTs from network or updating cache for $currentPublicKey",
                        e
                )
                // Don't clear local cache on network error, just log it.
                // The UI will continue showing the last cached data.
            }

            // Fetch XNS Names
            try {
                validXnsNames = networkService.getOwnedXnsNames(currentPublicKey)
                // *** ADD LOGGING HERE ***
                Log.d(
                        "WalletViewModel",
                        "Fetched ${validXnsNames.size} XNS names for $currentPublicKey: $validXnsNames"
                )
                if (validXnsNames.isNotEmpty()) {
                    // Fetch expirations only if names were found
                    val expirations = networkService.getXnsNameExpirationTimes(validXnsNames)
                    // Convert Instant? to epoch seconds Long?
                    xnsExpirationsMap =
                            expirations.mapValues { (_, instant) -> instant?.epochSecond }
                    Log.d("WalletViewModel", "Fetched expirations: $xnsExpirationsMap")
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error fetching XNS names or expirations", e)
                // Keep lists empty on error
                validXnsNames = emptyList()
                xnsExpirationsMap = emptyMap()
            } finally {
                _isNftLoading.value =
                        false // Mark NFT loading as complete (success or fail) - Moved here
            }

            // --- Update StateFlows --- //
            // Update Room (existing code)
            // ...

            // Update XNS StateFlows
            _ownedXnsNames.value = validXnsNames // Update with fetched names
            _xnsNameExpirations.value = xnsExpirationsMap // Update with fetched expirations

            // --- Update Displayed NFT --- //
            // This part needs refinement for reactivity. For now, update after network fetch.
            if (!hasLoadedInitialData) {
                val preferredContract = walletManager.getPreferredNftContract()
                // Get the current cached list (might have been updated by the Flow already)
                val currentCachedNfts = nftList.value
                val newDisplayedNft =
                        if (preferredContract != null) {
                            currentCachedNfts.find { it.contractAddress == preferredContract }
                                    ?: currentCachedNfts.firstOrNull()
                        } else {
                            currentCachedNfts.firstOrNull()
                        }
                // Only update if the displayed NFT actually changed
                if (_displayedNftInfo.value?.contractAddress != newDisplayedNft?.contractAddress) {
                    _displayedNftInfo.value = newDisplayedNft
                    Log.d(
                            "WalletViewModel",
                            "Updated displayed NFT (cache): ${newDisplayedNft?.contractAddress}"
                    )
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

            _isLoading.value = false
            // _isNftLoading is set within the NFT fetch block
            Log.d("WalletViewModel", "Network sync complete for $currentPublicKey")
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error during network sync", e)
            _isLoading.value = false
            _isNftLoading.value = false
        }
    }

    /** Update local cache with network data, detecting changes */
    private suspend fun updateCacheWithNetworkData(networkTokenInfoMap: Map<String, TokenInfo>) {
        try {
            networkTokenInfoMap.values.forEach { tokenInfo ->
                val cachedToken = tokenCacheDao.getTokenByContract(tokenInfo.contract)
                val currentTime = System.currentTimeMillis()

                val currentPublicKey = _publicKeyFlow.value
                val tokenEntity =
                        TokenCacheEntity(
                                contract = tokenInfo.contract,
                                name = tokenInfo.name,
                                symbol = tokenInfo.symbol,
                                decimals = 8, // Default decimals
                                logoUrl = tokenInfo.logoUrl,
                                isLogoCached = cachedToken?.isLogoCached ?: false,
                                lastUpdated = currentTime,
                                isActive = true,
                                // Keep existing balance fields if token exists, otherwise use
                                // defaults
                                cachedBalance = cachedToken?.cachedBalance ?: 0f,
                                balanceLastUpdated = cachedToken?.balanceLastUpdated ?: 0L,
                                ownerPublicKey = currentPublicKey
                        )

                // Check if data has changed
                val hasChanged =
                        cachedToken?.let { cached ->
                            cached.name != tokenInfo.name ||
                                    cached.symbol != tokenInfo.symbol ||
                                    cached.logoUrl != tokenInfo.logoUrl
                        }
                                ?: true

                if (hasChanged) {
                    tokenCacheDao.insertToken(tokenEntity)
                    Log.d(
                            "WalletViewModel",
                            "Updated cache for ${tokenInfo.contract}: ${tokenInfo.name}"
                    )

                    // Queue logo for caching if URL changed
                    if (tokenInfo.logoUrl != null && tokenInfo.logoUrl != cachedToken?.logoUrl) {
                        queueLogoForCaching(tokenInfo.symbol, tokenInfo.logoUrl, tokenInfo.contract)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error updating cache with network data", e)
        }
    }

    /** Queue logo for background caching */
    private fun queueLogoForCaching(symbol: String, logoUrl: String, contract: String) {
        viewModelScope.launch {
            try {
                val success = tokenLogoCacheManager.cacheTokenLogos(listOf(Pair(symbol, logoUrl)))
                if (success > 0) {
                    // Mark as cached in database
                    val tokenEntity = tokenCacheDao.getTokenByContract(contract)
                    tokenEntity?.let { tokenCacheDao.insertToken(it.copy(isLogoCached = true)) }
                    Log.d("WalletViewModel", "Successfully cached logo for $symbol")
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error caching logo for $symbol", e)
            }
        }
    }

    /** Legacy loadData method for compatibility */
    private fun loadData(force: Boolean) {
        if (force) {
            // Force reload from network
            viewModelScope.launch { syncWithNetwork() }
        } else {
            // Use cache-first approach
            loadFromCacheFirst()
        }
    }

    /**
     * Load token from cache first, then refresh from network in background This prevents logos from
     * disappearing when adding tokens
     */
    private fun loadTokenFromCacheFirst(contract: String) {
        viewModelScope.launch {
            val currentPublicKey = _publicKeyFlow.value
            if (currentPublicKey.isEmpty()) return@launch

            Log.d("WalletViewModel", "Loading token $contract from cache first")

            try {
                // Step 1: Load from cache immediately for instant UI feedback
                val cachedToken = tokenCacheDao.getTokenByContract(contract)
                if (cachedToken != null) {
                    Log.d("WalletViewModel", "Found cached token data for $contract")

                    // Update UI immediately with cached data
                    val currentTokenInfoMap = _tokenInfoMap.value.toMutableMap()
                    val tokenInfo =
                            TokenInfo(
                                    name = cachedToken.name,
                                    symbol = cachedToken.symbol,
                                    contract = cachedToken.contract,
                                    logoUrl = cachedToken.logoUrl
                            )
                    currentTokenInfoMap[contract] = tokenInfo
                    _tokenInfoMap.value = currentTokenInfoMap

                    // Pre-cache logo if available
                    if (cachedToken.logoUrl != null && cachedToken.isLogoCached) {
                        Log.d("WalletViewModel", "Logo already cached for $contract")
                    } else if (cachedToken.logoUrl != null) {
                        // Cache logo in background without blocking UI
                        launch {
                            tokenLogoCacheManager.cacheTokenLogoInBackground(
                                    cachedToken.logoUrl,
                                    cachedToken.symbol
                            )
                        }
                    }
                }

                // Step 2: Refresh from network in background and update only if changed
                refreshSingleTokenFromNetwork(contract)
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error loading token $contract from cache", e)
                // Fallback to network refresh
                refreshSingleTokenFromNetwork(contract)
            }
        }
    }

    /** Refresh a single token from network and update only if data changed */
    private suspend fun refreshSingleTokenFromNetwork(contract: String) {
        val currentPublicKey = _publicKeyFlow.value
        if (currentPublicKey.isEmpty()) return

        try {
            // Check connectivity
            if (!networkService.checkNodeConnectivity()) {
                Log.w("WalletViewModel", "Network not available, skipping refresh for $contract")
                return
            }

            // Get fresh data from network
            val predefinedToken = _predefinedTokens.value.find { it.contract == contract }
            val networkTokenInfo =
                    if (predefinedToken != null && predefinedToken.logoUrl != null) {
                        TokenInfo(
                                name = predefinedToken.name,
                                symbol = predefinedToken.symbol
                                                ?: predefinedToken.contract.takeLast(4).uppercase(),
                                contract = predefinedToken.contract,
                                logoUrl = predefinedToken.logoUrl
                        )
                    } else {
                        networkService.getTokenInfo(contract)
                    }

            val networkBalance = networkService.getTokenBalance(contract, currentPublicKey)

            // Check if data changed compared to current UI state
            val currentTokenInfo = _tokenInfoMap.value[contract]
            val dataChanged =
                    currentTokenInfo?.let { current ->
                        current.name != networkTokenInfo.name ||
                                current.symbol != networkTokenInfo.symbol ||
                                current.logoUrl != networkTokenInfo.logoUrl
                    }
                            ?: true

            // Update UI only if data actually changed
            if (dataChanged) {
                Log.d("WalletViewModel", "Network data changed for $contract, updating UI")
                val currentTokenInfoMap = _tokenInfoMap.value.toMutableMap()
                currentTokenInfoMap[contract] = networkTokenInfo
                _tokenInfoMap.value = currentTokenInfoMap

                // Update cache with new network data
                updateCacheWithNetworkData(mapOf(contract to networkTokenInfo))
            } else {
                Log.d("WalletViewModel", "Network data unchanged for $contract, skipping UI update")
            }

            // Always update balance
            val currentBalanceMap = _balanceMap.value.toMutableMap()
            currentBalanceMap[contract] = networkBalance
            _balanceMap.value = currentBalanceMap

            Log.d(
                    "WalletViewModel",
                    "Token refresh complete for $contract - balance: $networkBalance"
            )
        } catch (e: Exception) {
            Log.e("WalletViewModel", "Error refreshing token $contract from network", e)
        }
    }

    /**
     * Refresh only token-related data (balances, info) without fetching NFTs This is used when
     * adding/removing tokens to avoid unnecessary NFT verification
     */
    private fun refreshTokenDataOnly() {
        viewModelScope.launch {
            val currentPublicKey = _publicKeyFlow.value
            if (currentPublicKey.isEmpty()) return@launch

            Log.d("WalletViewModel", "Refreshing token data only for key: $currentPublicKey")

            _isLoading.value = true

            try {
                // Check connectivity
                _isCheckingConnection.value = true
                _isNodeConnected.value = networkService.checkNodeConnectivity()
                _isCheckingConnection.value = false

                if (!_isNodeConnected.value) {
                    Log.w("WalletViewModel", "Network not available, skipping token refresh")
                    _isLoading.value = false
                    return@launch
                }

                val currentTokens = _tokens.value
                val networkTokenInfoMap = mutableMapOf<String, TokenInfo>()
                val networkBalanceMap = mutableMapOf<String, Float>()

                // Fetch fresh token data from network (excluding NFTs)
                currentTokens.forEach { contract ->
                    try {
                        // Get token info from network
                        val predefinedToken =
                                _predefinedTokens.value.find { it.contract == contract }
                        val tokenInfo =
                                if (predefinedToken != null && predefinedToken.logoUrl != null) {
                                    TokenInfo(
                                            name = predefinedToken.name,
                                            symbol = predefinedToken.symbol
                                                            ?: predefinedToken
                                                                    .contract
                                                                    .takeLast(4)
                                                                    .uppercase(),
                                            contract = predefinedToken.contract,
                                            logoUrl = predefinedToken.logoUrl
                                    )
                                } else {
                                    networkService.getTokenInfo(contract)
                                }

                        networkTokenInfoMap[contract] = tokenInfo

                        // Get balance from network
                        val balance = networkService.getTokenBalance(contract, currentPublicKey)
                        networkBalanceMap[contract] = balance

                        Log.d("WalletViewModel", "Token refresh: $contract - balance: $balance")
                    } catch (e: Exception) {
                        Log.e("WalletViewModel", "Error fetching token data for $contract", e)
                    }
                }

                // Update cache with fresh network data
                updateCacheWithNetworkData(networkTokenInfoMap)

                // Update UI with network data
                _tokenInfoMap.value = networkTokenInfoMap
                _balanceMap.value = networkBalanceMap

                // Cache token logos after network sync
                cacheTokenLogosFromInfoMap(networkTokenInfoMap)

                Log.d(
                        "WalletViewModel",
                        "Token data refresh complete for ${currentTokens.size} tokens"
                )
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error during token data refresh", e)
            } finally {
                _isLoading.value = false
            }
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

        if (!force &&
                        _transactionHistory.value.isNotEmpty() &&
                        _transactionHistoryError.value == null
        ) {
            Log.d(
                    "WalletViewModel",
                    "Transaction history already loaded and no error, skipping reload unless forced."
            )
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
                    Log.d(
                            "WalletViewModel",
                            "Loaded ${history.size} transactions for key: $currentKey"
                    )
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error loading transaction history", e)
                _transactionHistoryError.value =
                        "Failed to load transaction history: ${e.localizedMessage}"
                _transactionHistory.value = EMPTY_TRANSACTION_HISTORY // Clear history on error
            } finally {
                _isTransactionHistoryLoading.value = false
            }
        }
    }

    // --- Token-Specific Transaction History Loading Function ---
    fun loadTokenTransactionHistory(tokenContract: String, force: Boolean = false) {
        val currentKey = _publicKeyFlow.value
        if (currentKey.isEmpty()) {
            Log.w("WalletViewModel", "Cannot load token transaction history, public key is empty.")
            _tokenTransactionHistory.value = EMPTY_TRANSACTION_HISTORY
            _isTokenTransactionHistoryLoading.value = false
            _tokenTransactionHistoryError.value = null
            return
        }

        if (!force &&
                        _tokenTransactionHistory.value.isNotEmpty() &&
                        _tokenTransactionHistoryError.value == null
        ) {
            Log.d(
                    "WalletViewModel",
                    "Token transaction history already loaded and no error, skipping reload unless forced."
            )
            return
        }

        viewModelScope.launch {
            Log.d(
                    "WalletViewModel",
                    "Loading token transaction history for contract: $tokenContract, key: $currentKey"
            )
            _isTokenTransactionHistoryLoading.value = true
            _tokenTransactionHistoryError.value = null

            try {
                val history = transactionRepository.getTokenTransactions(currentKey, tokenContract)
                _tokenTransactionHistory.value = history

                if (history.isEmpty()) {
                    Log.d(
                            "WalletViewModel",
                            "No token transaction history found for contract: $tokenContract, key: $currentKey"
                    )
                } else {
                    Log.d(
                            "WalletViewModel",
                            "Loaded ${history.size} token transactions for contract: $tokenContract, key: $currentKey"
                    )
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error loading token transaction history", e)
                _tokenTransactionHistoryError.value =
                        "Failed to load token transaction history: ${e.localizedMessage}"
                _tokenTransactionHistory.value = EMPTY_TRANSACTION_HISTORY // Clear history on error
            } finally {
                _isTokenTransactionHistoryLoading.value = false
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

    /** Preload predefined tokens to cache to ensure they exist for balance caching */
    private fun preloadPredefinedTokensToCache(): kotlinx.coroutines.Job {
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPublicKey = _publicKeyFlow.value
                Log.d("WalletViewModel", "Preload starting with publicKey: '$currentPublicKey'")
                if (currentPublicKey.isEmpty()) {
                    Log.w(
                            "WalletViewModel",
                            "No active public key, skipping predefined tokens preload"
                    )
                    return@launch
                }

                val currentTime = System.currentTimeMillis()

                // ALWAYS ensure currency token (XIAN) exists in cache by default
                val currencyToken = tokenCacheDao.getTokenWithBalance("currency", currentPublicKey)
                if (currencyToken == null) {
                    val xianTokenEntity =
                            TokenCacheEntity(
                                    contract = "currency",
                                    name = "Xian",
                                    symbol = "XIAN",
                                    decimals = 8,
                                    logoUrl = null, // Will use drawable resource for XIAN logo
                                    isActive = true,
                                    lastUpdated = currentTime,
                                    cachedBalance = 0f,
                                    balanceLastUpdated =
                                            0L, // No balance cached yet - will be set when network
                                    // data is fetched
                                    ownerPublicKey = currentPublicKey
                            )
                    tokenCacheDao.insertToken(xianTokenEntity)
                    Log.d("WalletViewModel", "Automatically added currency token (XIAN) to cache")
                } else {
                    Log.d("WalletViewModel", "Currency token (XIAN) already exists in cache")
                }

                Log.d(
                        "WalletViewModel",
                        "Preloading ${_internalPredefinedTokens.size} predefined tokens"
                )

                _internalPredefinedTokens.forEach { predefinedToken ->
                    val existingToken =
                            tokenCacheDao.getTokenWithBalance(
                                    predefinedToken.contract,
                                    currentPublicKey
                            )

                    if (existingToken == null) {
                        // Create new token entry in cache with initial balance 0.0
                        val newTokenEntity =
                                TokenCacheEntity(
                                        contract = predefinedToken.contract,
                                        name = predefinedToken.name,
                                        symbol = predefinedToken.symbol
                                                        ?: predefinedToken
                                                                .contract
                                                                .takeLast(4)
                                                                .uppercase(),
                                        decimals = 8,
                                        logoUrl = predefinedToken.logoUrl,
                                        isActive = true,
                                        lastUpdated = currentTime,
                                        cachedBalance = 0f,
                                        balanceLastUpdated =
                                                0L, // No balance cached yet - will be set when
                                        // network data is fetched
                                        ownerPublicKey = currentPublicKey
                                )

                        tokenCacheDao.insertToken(newTokenEntity)
                        Log.d(
                                "WalletViewModel",
                                "Preloaded predefined token to cache: ${predefinedToken.name} (${predefinedToken.contract})"
                        )

                        // Verify insertion was successful
                        val verifyInserted =
                                tokenCacheDao.getTokenWithBalance(
                                        predefinedToken.contract,
                                        currentPublicKey
                                )
                        Log.d(
                                "WalletViewModel",
                                "Verification after preload insert for ${predefinedToken.contract}: ${if (verifyInserted != null) "SUCCESS" else "FAILED"}"
                        )
                    } else {
                        Log.d(
                                "WalletViewModel",
                                "Predefined token already exists in cache: ${predefinedToken.name}"
                        )
                    }
                }

                Log.d(
                        "WalletViewModel",
                        "Predefined tokens preload completed for public key: $currentPublicKey"
                )
            } catch (e: Exception) {
                Log.e(
                        "WalletViewModel",
                        "Error preloading predefined tokens to cache: ${e.message}",
                        e
                )
            }
        }
    }

    // --- Token Logo Caching Methods ---

    /** Cache token logos for predefined tokens */
    private fun cacheTokenLogos(tokens: List<PredefinedToken>) {
        viewModelScope.launch {
            try {
                val tokensToCache =
                        tokens.mapNotNull { token ->
                            token.logoUrl?.let { logoUrl ->
                                Pair(
                                        token.symbol ?: token.contract.takeLast(4).uppercase(),
                                        logoUrl
                                )
                            }
                        }

                if (tokensToCache.isNotEmpty()) {
                    val cachedCount = tokenLogoCacheManager.cacheTokenLogos(tokensToCache)
                    Log.d(
                            "WalletViewModel",
                            "Successfully cached $cachedCount/${tokensToCache.size} predefined token logos"
                    )

                    // Update database cache status for predefined tokens
                    tokens.forEach { token ->
                        if (token.logoUrl != null) {
                            val tokenEntity =
                                    TokenCacheEntity(
                                            contract = token.contract,
                                            name = token.name,
                                            symbol = token.symbol
                                                            ?: token.contract
                                                                    .takeLast(4)
                                                                    .uppercase(),
                                            decimals = 8,
                                            logoUrl = token.logoUrl,
                                            isLogoCached =
                                                    tokenLogoCacheManager.isLogoCached(
                                                            token.logoUrl
                                                    )
                                    )
                            tokenCacheDao.insertToken(tokenEntity)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error caching predefined token logos", e)
            }
        }
    }

    /** Cache token logos from TokenInfo map */
    private fun cacheTokenLogosFromInfoMap(tokenInfoMap: Map<String, TokenInfo>) {
        viewModelScope.launch {
            try {
                val tokensToCache =
                        tokenInfoMap.values.mapNotNull { tokenInfo ->
                            tokenInfo.logoUrl?.let { logoUrl -> Pair(tokenInfo.symbol, logoUrl) }
                        }

                if (tokensToCache.isNotEmpty()) {
                    val cachedCount = tokenLogoCacheManager.cacheTokenLogos(tokensToCache)
                    Log.d(
                            "WalletViewModel",
                            "Successfully cached $cachedCount/${tokensToCache.size} token logos from info map"
                    )

                    // Update database cache status for tokens
                    tokenInfoMap.values.forEach { tokenInfo ->
                        if (tokenInfo.logoUrl != null) {
                            val tokenEntity =
                                    TokenCacheEntity(
                                            contract = tokenInfo.contract,
                                            name = tokenInfo.name,
                                            symbol = tokenInfo.symbol,
                                            decimals = 8,
                                            logoUrl = tokenInfo.logoUrl,
                                            isLogoCached =
                                                    tokenLogoCacheManager.isLogoCached(
                                                            tokenInfo.logoUrl
                                                    )
                                    )
                            tokenCacheDao.insertToken(tokenEntity)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error caching token logos from info map", e)
            }
        }
    }

    /** Get the ImageLoader for use with AsyncImage composables */
    fun getImageLoader() = tokenLogoCacheManager.imageLoader
    fun getNftImageLoader() = nftImageCacheManager.imageLoader

    /** Preload token logos from database cache on startup */
    private fun preloadTokenLogosFromCache() {
        viewModelScope.launch {
            try {
                // Get tokens that need logo caching from database
                val tokensNeedingCache = tokenCacheDao.getTokensNeedingLogoCache()

                if (tokensNeedingCache.isNotEmpty()) {
                    val tokensToCache =
                            tokensNeedingCache.map { entity ->
                                Pair(entity.symbol, entity.logoUrl!!)
                            }

                    val cachedCount = tokenLogoCacheManager.cacheTokenLogos(tokensToCache)
                    Log.d(
                            "WalletViewModel",
                            "Preloaded $cachedCount/${tokensToCache.size} token logos from cache"
                    )

                    // Mark as cached in database using proper async cache verification
                    tokensNeedingCache.forEach { entity ->
                        launch {
                            if (tokenLogoCacheManager.isLogoCached(entity.logoUrl)) {
                                tokenCacheDao.markLogoAsCached(entity.contract)
                                Log.d(
                                        "WalletViewModel",
                                        "Marked logo as cached for ${entity.contract}"
                                )
                            } else {
                                Log.w(
                                        "WalletViewModel",
                                        "Logo cache verification failed for ${entity.contract}: ${entity.logoUrl}"
                                )
                            }
                        }
                    }
                }

                // Also preload predefined token logos that might not be in database yet
                cacheTokenLogos(_internalPredefinedTokens)
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error preloading token logos from cache", e)
            }
        }
    }

    /** Check if a token logo is cached */
    suspend fun isTokenLogoCached(logoUrl: String?): Boolean {
        return tokenLogoCacheManager.isLogoCached(logoUrl)
    }

    /** Get cache statistics for debugging */
    suspend fun getTokenCacheStats() = tokenLogoCacheManager.getCacheStats()

    /** Dump NFT image cache state to audit log and return a human-readable summary. */
    suspend fun dumpNftCacheState(): String =
            withContext(Dispatchers.IO) { nftImageCacheManager.dumpState() }

    /** Return the full filesystem path to the NFT cache audit log file. */
    fun getNftCacheAuditLogPath(): String = nftImageCacheManager.getAuditLogFile().absolutePath

    /** Clear all token logo caches (for debugging/settings) */
    fun clearTokenLogoCache() {
        viewModelScope.launch {
            try {
                tokenLogoCacheManager.clearCache()
                tokenCacheDao.resetAllLogoCacheStatus()
                Log.d("WalletViewModel", "Cleared all token logo caches")
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error clearing token logo cache", e)
            }
        }
    }

    // --- Time Period Chart Helper Methods ---

    /** Get historical data for specific time period */
    private suspend fun getHistoricalDataForPeriod(
            tokenContract: String,
            timePeriod: String
    ): List<FloatEntry> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all pairs to find the pair for this token
                val allPairs = networkService.getAllPairs()
                val tokenPair =
                        allPairs.find { pair ->
                            pair.token0 == tokenContract || pair.token1 == tokenContract
                        }

                if (tokenPair == null) {
                    Log.w("WalletViewModel", "No trading pair found for token: $tokenContract")
                    return@withContext emptyList()
                }

                // Fetch swap events from network with time period parameter
                val swapEvents = networkService.getSwapEventsForPair(tokenPair.id, timePeriod)
                Log.d(
                        "WalletViewModel",
                        "Fetched ${swapEvents.size} swap events for pair ${tokenPair.id} with period $timePeriod"
                )

                if (swapEvents.isEmpty()) {
                    Log.w("WalletViewModel", "No swap events found for pair: ${tokenPair.id}")
                    return@withContext emptyList()
                }

                // Filter events based on selected time period
                val filteredEvents = filterEventsByTimePeriod(swapEvents, timePeriod)
                Log.d(
                        "WalletViewModel",
                        "Filtered to ${filteredEvents.size} events for $timePeriod period"
                )

                if (filteredEvents.isEmpty()) {
                    Log.w("WalletViewModel", "No events found for $timePeriod period")
                    return@withContext emptyList()
                }

                // Process filtered events into chart data
                processSwapEventsToChartData(filteredEvents, tokenContract, tokenPair)
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error fetching historical data for $timePeriod", e)
                emptyList()
            }
        }
    }

    /** Filter swap events based on the selected time period */
    private fun filterEventsByTimePeriod(
            swapEvents: List<SwapEvent>,
            timePeriod: String
    ): List<SwapEvent> {
        val now = java.time.Instant.now()
        val cutoffTime =
                when (timePeriod) {
                    "1H" -> now.minus(1, java.time.temporal.ChronoUnit.HOURS)
                    "1D" -> now.minus(1, java.time.temporal.ChronoUnit.DAYS)
                    "1W" -> now.minus(7, java.time.temporal.ChronoUnit.DAYS)
                    "1M" -> now.minus(30, java.time.temporal.ChronoUnit.DAYS)
                    "1Y" -> now.minus(365, java.time.temporal.ChronoUnit.DAYS)
                    else -> now.minus(1, java.time.temporal.ChronoUnit.DAYS) // Default to 1 day
                }

        return swapEvents.filter { event ->
            try {
                val eventTime = java.time.Instant.parse(event.timestamp + "Z")
                eventTime.isAfter(cutoffTime)
            } catch (e: Exception) {
                Log.w("WalletViewModel", "Could not parse event timestamp: ${event.timestamp}")
                false
            }
        }
    }

    /**
     * Resample existing data for different time periods This ensures all data points fit
     * comfortably in the chart without scrolling
     */
    private fun resampleDataForPeriod(
            originalData: List<FloatEntry>,
            timePeriod: String
    ): List<FloatEntry> {
        if (originalData.isEmpty()) return emptyList()

        // Limit data points to fit comfortably in the chart without scrolling
        val maxDataPoints =
                when (timePeriod) {
                    "1H" -> 12 // Show data every 5 minutes
                    "1D" -> 24 // Show hourly data points
                    "1W" -> 7 // Show daily data points
                    "1M" -> 30 // Show daily data points
                    "1Y" -> 12 // Show monthly data points
                    else -> 20 // Default fallback
                }

        return if (originalData.size <= maxDataPoints) {
            // If we have less data than target, return original
            originalData
        } else {
            // Resample by taking evenly distributed points
            val step = originalData.size.toDouble() / maxDataPoints
            val sampledData = mutableListOf<FloatEntry>()

            for (i in 0 until maxDataPoints) {
                val index = (i * step).toInt()
                if (index < originalData.size) {
                    // Re-index the entry to have sequential X values for proper display
                    sampledData.add(FloatEntry(i.toFloat(), originalData[index].y))
                }
            }

            sampledData
        }
    }

    // === Portfolio 7D Performance helpers ===

    private fun shouldRecomputePortfolioPerf(newSnap: PortfolioSnapshot): Boolean {
        // Compare current composition (by percent) with last computed
        val newWeights =
                newSnap.tokens.filter { it.usdValue > 0f }.associate {
                    it.contract to it.percent.coerceAtLeast(0f)
                }
        if (lastCompositionWeights.isEmpty()) {
            lastCompositionWeights = newWeights
            return true
        }
        // Sum absolute diffs across union of keys
        val keys = lastCompositionWeights.keys + newWeights.keys
        var diff = 0f
        keys.forEach { k ->
            val a = lastCompositionWeights[k] ?: 0f
            val b = newWeights[k] ?: 0f
            diff += abs(a - b)
        }
        val should = diff > 1.0f // > 1 percentage point change
        if (should) lastCompositionWeights = newWeights
        return should
    }

    private fun computePortfolio7dPerformance(snap: PortfolioSnapshot) {
        // Launch heavy work on IO
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val xianUsd = snap.xianPrice
                val allPairs =
                        try {
                            networkService.getAllPairs()
                        } catch (_: Exception) {
                            emptyList()
                        }
                val nowHour =
                        java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                val startHour = nowHour.minus(167, java.time.temporal.ChronoUnit.HOURS)
                val startEpoch = startHour.epochSecond

                // Build per-token hourly USD price series (7d, 168 pts)
                data class SeriesResult(
                        val contract: String,
                        val symbol: String?,
                        val balance: Float,
                        val series: List<Double>,
                        val usedFallback: Boolean
                )
                val perToken =
                        coroutineScope {
                            snap.tokens
                                    .map { entry ->
                                        async(Dispatchers.IO) {
                                            val contract = entry.contract
                                            val symbol = entry.symbol
                                            val balance = entry.balance
                                            // Special handling for USDC-like stable if present
                                            if (contract == "con_usdc") {
                                                val ones = List(168) { 1.0 }
                                                SeriesResult(contract, symbol, balance, ones, false)
                                            } else {
                                                val (s, fb) =
                                                        fetchHourlyPriceSeries7dUSD(
                                                                contract,
                                                                allPairs,
                                                                xianUsd,
                                                                startEpoch
                                                        )
                                                SeriesResult(contract, symbol, balance, s, fb)
                                            }
                                        }
                                    }
                                    .awaitAll()
                        }
                                .filter { it.series.size == 168 && it.balance > 0f }

                if (perToken.isEmpty()) {
                    _portfolio7dPerformance.value = emptyList()
                    _tokenContributions.value = emptyList()
                    _portfolioPerfUsedFallback.value = false
                    return@launch
                }

                // Compute weights at t0 using price0 and balances; ignore weights < 0.1%
                val price0ByToken = perToken.associate { it.contract to it.series.first() }
                val usd0ByToken =
                        perToken.associate { t ->
                            t.contract to (t.balance * (price0ByToken[t.contract] ?: 0.0))
                        }
                val total0 = usd0ByToken.values.sum().toFloat().coerceAtLeast(1e-6f)

                val weights =
                        usd0ByToken
                                .mapValues { (_, v) -> (v.toFloat() / total0).coerceIn(0f, 1f) }
                                .filterValues { it >= 0.001f } // >= 0.1%

                if (weights.isEmpty()) {
                    _portfolio7dPerformance.value = emptyList()
                    _tokenContributions.value = emptyList()
                    _portfolioPerfUsedFallback.value = false
                    return@launch
                }

                // Per-token relative perf series (%), then weighted sum
                val tokenPerfPct: Map<String, List<Float>> =
                        perToken.associate { t ->
                            val p0 = t.series.first().coerceAtLeast(1e-12)
                            val rel = t.series.map { (((it / p0) - 1.0) * 100.0).toFloat() }
                            t.contract to rel
                        }

                val portfolioPerf = MutableList(168) { 0f }
                for (i in 0 until 168) {
                    var acc = 0f
                    weights.forEach { (contract, w) ->
                        val s = tokenPerfPct[contract]
                        if (s != null && i < s.size) acc += (w * s[i])
                    }
                    // Round to 2 decimals per constraints
                    portfolioPerf[i] = kotlin.math.round(acc * 100f) / 100f
                }

                // Token contributions to final curve (weight * final change)
                val contributions =
                        weights
                                .map { (contract, w) ->
                                    val tokenSeries = tokenPerfPct[contract] ?: emptyList()
                                    val finalChange =
                                            if (tokenSeries.isNotEmpty()) tokenSeries.last() else 0f
                                    val finalContribution =
                                            kotlin.math.round((w * finalChange) * 100f) / 100f
                                    val sym = perToken.find { it.contract == contract }?.symbol
                                    if (DEBUG_PERF) {
                                        val seriesRaw =
                                                perToken.find { it.contract == contract }?.series
                                        val p0 = seriesRaw?.firstOrNull()
                                        val plast = seriesRaw?.lastOrNull()
                                        Log.d(
                                                "PerfDebug",
                                                "contract=$contract symbol=$sym p0=$p0 plast=$plast w=${w * 100f}% finalChange=${"%.4f".format(finalChange)} contribution=${"%.4f".format(finalContribution)}"
                                        )
                                    }
                                    TokenContribution(
                                            contract = contract,
                                            symbol = sym,
                                            weightPercent =
                                                    kotlin.math.round(w * 10000f) /
                                                            100f, // 2 decimals
                                            finalContributionPercent = finalContribution,
                                            token7dChangePercent =
                                                    kotlin.math.round(finalChange * 100f) / 100f
                                    )
                                }
                                .sortedByDescending { kotlin.math.abs(it.finalContributionPercent) }

                val usedFallbackAny = perToken.any { it.usedFallback }

                // Update state
                _portfolio7dPerformance.value = portfolioPerf
                _tokenContributions.value = contributions
                _portfolioPerfUsedFallback.value = usedFallbackAny
            } catch (e: Exception) {
                Log.e("WalletViewModel", "Error computing portfolio 7D performance", e)
                _portfolio7dPerformance.value = emptyList()
                _tokenContributions.value = emptyList()
                _portfolioPerfUsedFallback.value = false
            }
        }
    }

    private suspend fun fetchHourlyPriceSeries7dUSD(
            tokenContract: String,
            allPairs: List<PairInfo>,
            xianUsd: Float,
            epochHourStart: Long
    ): Pair<List<Double>, Boolean> {
        try {
            val pair =
                    if (tokenContract == "currency") {
                        allPairs.find {
                            (it.token0 == "currency" && it.token1 == "con_usdc") ||
                                    (it.token1 == "currency" && it.token0 == "con_usdc")
                        }
                    } else {
                        allPairs.find {
                            (it.token0 == tokenContract && it.token1 == "currency") ||
                                    (it.token1 == tokenContract && it.token0 == "currency")
                        }
                    }
                            ?: return Pair(emptyList(), true)

            // Attempt cache hit (within current hour window)
            priceSeriesCache[pair.id]?.let { cached ->
                if (cached.epochHourStart == epochHourStart) {
                    return Pair(cached.pointsUsd, cached.usedFallback)
                }
            }

            val swapEvents = networkService.getSwapEventsForPair(pair.id, "1W")
            if (swapEvents.isEmpty()) {
                // Fallback: reuse latest available point
                val baseUsd: Double =
                        when (tokenContract) {
                            "con_usdc" -> 1.0
                            "currency" -> xianUsd.toDouble()
                            else -> {
                                val pxInXian =
                                        try {
                                            tokenPriceRepository
                                                    .getTokenPrice(tokenContract)
                                                    .first()
                                        } catch (_: Exception) {
                                            null
                                        }
                                if (pxInXian != null && pxInXian > 0f)
                                        (pxInXian * xianUsd).toDouble()
                                else 0.0
                            }
                        }
                if (baseUsd > 0.0) {
                    val points = List(168) { baseUsd }
                    priceSeriesCache[pair.id] =
                            PriceSeriesCache(
                                    pointsUsd = points,
                                    epochHourStart = epochHourStart,
                                    generatedAt = System.currentTimeMillis(),
                                    usedFallback = true
                            )
                    return Pair(points, true)
                }
                return Pair(emptyList(), true)
            }

            // Bucket by hour using VWAP
            val hourMap =
                    mutableMapOf<
                            Long, Pair<Double, Double>>() // hourEpoch -> (sum(price*vol), sum(vol))
            swapEvents.forEach { ev ->
                try {
                    val ts = java.time.Instant.parse(ev.timestamp + "Z")
                    val hour = ts.truncatedTo(java.time.temporal.ChronoUnit.HOURS).epochSecond
                    var price = ev.price
                    // Normalize price orientation depending on pair layout.
                    // API assumption (potential source of bug antes): ev.price is quoted as
                    // token0/token1.
                    // We want:
                    //  - For currency vs USDC pair: USDC per XIAN (i.e., price in USD). If
                    // token0=="currency" and token1=="con_usdc",
                    //    then ev.price = currency/usdc => already XIAN per USDC, so invert to get
                    // USDC per XIAN.
                    //    If token1=="currency" and token0=="con_usdc", ev.price = usdc/currency =>
                    // already USDC per XIAN (no invert).
                    //  - For generic token vs currency pair: need XIAN per token. If token0 ==
                    // tokenContract and token1 == currency,
                    //    ev.price = token/currency => already token per XIAN (need invert). If
                    // token0 == currency and token1 == token,
                    //    ev.price = currency/token => already XIAN per token (no invert).
                    // Old logic invert = (tokenContract == pair.token0 OR currency special) causaba
                    // signo invertido.
                    // User report: still seeing inverted 7D changes => ev.price likely represents
                    // token1/token0
                    // So we flip the inversion rule: now we invert when the *other* orientation is
                    // present.
                    val invert =
                            if (tokenContract == "currency") {
                                // currency <-> USDC: if currency is *token1*, ev.price ~
                                // token1/token0 => currency/usdc, need USDC per currency => no
                                // invert.
                                // If currency is token0 (previous logic), now we do NOT invert;
                                // instead invert when currency is token1.
                                pair.token1 == "currency" && pair.token0 == "con_usdc"
                            } else {
                                // token <-> currency: invert when token is token1 (opposite of
                                // previous assumption)
                                pair.token1 == tokenContract && pair.token0 == "currency"
                            }
                    if (invert) {
                        price = 1.0 / price
                        if (DEBUG_PERF)
                                Log.d(
                                        "PerfDebug",
                                        "Inverted price for contract=$tokenContract pair=${pair.id}"
                                )
                    }
                    val value = price * ev.volume
                    val (sumV, sumVol) = hourMap[hour] ?: (0.0 to 0.0)
                    hourMap[hour] = (sumV + value) to (sumVol + ev.volume)
                } catch (_: Exception) {
                    // ignore bad timestamp
                }
            }

            // Build 168-point hourly series [oldest..newest], forward-fill gaps with last known
            // price
            val points = ArrayList<Double>(168)
            var lastPrice: Double? = null
            var usedFallback = false
            val nowHour =
                    java.time.Instant.ofEpochSecond(epochHourStart)
                            .plus(167, java.time.temporal.ChronoUnit.HOURS)
            for (i in 0 until 168) {
                val hour = epochHourStart + i * 3600
                val v =
                        hourMap[hour]?.let { (sumV, sumVol) ->
                            if (sumVol > 0.0) sumV / sumVol else lastPrice
                        }
                                ?: lastPrice
                val base =
                        if (v == null) {
                            // no prior price yet, attempt to use nearest future available as
                            // initial reference
                            // find the next known hour within window
                            val nextKnown =
                                    (0 until 168).firstNotNullOfOrNull { j ->
                                        val h = epochHourStart + j * 3600
                                        hourMap[h]?.let { (sv, svl) ->
                                            if (svl > 0.0) sv / svl else null
                                        }
                                    }
                            if (nextKnown != null) {
                                usedFallback = true
                                nextKnown
                            } else {
                                usedFallback = true
                                0.0
                            }
                        } else v
                lastPrice = base
                // Convert to USD if needed
                val usdPrice =
                        if (tokenContract == "currency") {
                            base // USDC per XIAN (already USD)
                        } else {
                            base * xianUsd
                        }
                points.add(usdPrice)
            }

            // Cache
            priceSeriesCache[pair.id] =
                    PriceSeriesCache(
                            pointsUsd = points,
                            epochHourStart = epochHourStart,
                            generatedAt = System.currentTimeMillis(),
                            usedFallback = usedFallback
                    )
            return Pair(points, usedFallback)
        } catch (e: Exception) {
            Log.e("WalletViewModel", "fetchHourlyPriceSeries7dUSD error for $tokenContract", e)
            return Pair(emptyList(), true)
        }
    }
} // End of WalletViewModel class
