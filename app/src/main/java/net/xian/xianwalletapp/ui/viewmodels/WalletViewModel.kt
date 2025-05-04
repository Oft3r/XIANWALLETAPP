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

// Define default empty states
private val EMPTY_TOKEN_INFO_MAP: Map<String, TokenInfo> = emptyMap()
private val EMPTY_BALANCE_MAP: Map<String, Float> = emptyMap()
// private val EMPTY_NFT_LIST: List<NftInfo> = emptyList() // Replaced by Flow
private val EMPTY_NFT_CACHE_LIST: List<NftCacheEntity> = emptyList() // Default for Flow
private val EMPTY_XNS_NAME_LIST: List<String> = emptyList() // Added for XNS names
private val EMPTY_XNS_EXPIRATIONS: Map<String, Long?> = emptyMap() // Added for expirations

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
    private val nftCacheDao: NftCacheDao // Add DAO as dependency
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

    private val _resolvedXnsAddress = MutableStateFlow<String?>(null)
    val resolvedXnsAddress: StateFlow<String?> = _resolvedXnsAddress.asStateFlow()

    private val _isXnsAddress = MutableStateFlow(false) // True if input is valid XNS and resolved
    val isXnsAddress: StateFlow<Boolean> = _isXnsAddress.asStateFlow()

    private val _isResolvingXns = MutableStateFlow(false)
    val isResolvingXns: StateFlow<Boolean> = _isResolvingXns.asStateFlow()

    // --- Fee Estimation State Flow ---
    private val _estimatedFeeState = MutableStateFlow<FeeEstimationState>(FeeEstimationState.Idle)
    val estimatedFeeState: StateFlow<FeeEstimationState> = _estimatedFeeState.asStateFlow()
    private var currentStampRate: Float? = null // Cache stamp rate

    // Flag to prevent initial load if data already exists (e.g., ViewModel survived)
    private var hasLoadedInitialData = false

    init {
        // Observe the active wallet public key flow from WalletManager
        // Ensure the initial public key state is set correctly
        _publicKeyFlow.value = walletManager.getActiveWalletPublicKey() ?: ""
        
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
                        hasLoadedInitialData = false // Reset flag to force reload for the new wallet
                        _ownedXnsNames.value = EMPTY_XNS_NAME_LIST // Clear XNS names
                        _xnsNameExpirations.value = EMPTY_XNS_EXPIRATIONS // Clear expirations
                        // No need to clear _nftList, the flatMapLatest will switch the source Flow
                        _displayedNftInfo.value = null // Clear displayed NFT
                        if (newKey.isNotEmpty()) {
                            loadData(force = true) // Trigger data load for the new active wallet
                        } else {
                            // Handle case where all wallets might be deleted
                            Log.w("WalletViewModel", "Active wallet key became null.")
                            _tokens.value = emptyList()
                            _tokenInfoMap.value = EMPTY_TOKEN_INFO_MAP
                            _balanceMap.value = EMPTY_BALANCE_MAP
                            _isLoading.value = false
                            _isNftLoading.value = false
                        }
                    }
                }
                isInitialValue = false // Mark initial value as processed
            }
        }
        // Trigger initial data load explicitly after setting up observer
        loadDataIfNotLoaded()
        // Start periodic connectivity check
        startConnectivityChecks()
    }

    // --- Public Functions for UI Interaction ---

    fun refreshData() {
        Log.d("WalletViewModel", "Manual refresh triggered.")
        // Reset token list from manager in case it changed
        _tokens.value = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
        // Force load data
        loadData(force = true)
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

            // Fetch XIAN price info (unchanged)
            try {
                val priceInfo = networkService.getXianPriceInfo()
                _xianPriceInfo.value = priceInfo
                _xianPrice.value = priceInfo?.let { (reserve0, reserve1) -> if (reserve1 != 0f) reserve0 / reserve1 else 0f }
                Log.d("WalletViewModel", "Fetched XIAN Price: ${_xianPrice.value} (Reserves: $priceInfo)")
            } catch (e: Exception) {
                 Log.e("WalletViewModel", "Error fetching XIAN price info", e)
            }

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
                // --- Room Update Complete --- //

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