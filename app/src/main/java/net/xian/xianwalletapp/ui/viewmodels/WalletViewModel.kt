package net.xian.xianwalletapp.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.xian.xianwalletapp.network.NftInfo
import net.xian.xianwalletapp.network.TokenInfo
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager
import net.xian.xianwalletapp.network.TransactionResult // Added import
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject // Added import
import java.math.BigDecimal // Added import
import java.text.NumberFormat // For fee formatting
import java.util.Locale // For fee formatting

// Define default empty states
private val EMPTY_TOKEN_INFO_MAP: Map<String, TokenInfo> = emptyMap()
private val EMPTY_BALANCE_MAP: Map<String, Float> = emptyMap()
private val EMPTY_NFT_LIST: List<NftInfo> = emptyList()

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
    private val networkService: XianNetworkService
) : ViewModel() {

    private val _publicKey = mutableStateOf(walletManager.getPublicKey() ?: "")
    val publicKey: State<String> = _publicKey

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

    private val _nftList = MutableStateFlow<List<NftInfo>>(EMPTY_NFT_LIST)
    val nftList: StateFlow<List<NftInfo>> = _nftList.asStateFlow()

    private val _displayedNftInfo = MutableStateFlow<NftInfo?>(null)
    val displayedNftInfo: StateFlow<NftInfo?> = _displayedNftInfo.asStateFlow()

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
        _publicKey.value = walletManager.getActiveWalletPublicKey() ?: ""

        // Observe the active wallet public key flow for CHANGES
        viewModelScope.launch {
            var isInitialValue = true // Flag to skip reaction to the very first emission if needed
            walletManager.activeWalletPublicKeyFlow.collect { activeKey ->
                Log.d("WalletViewModel", "Observed active key change: $activeKey (isInitialValue: $isInitialValue)")

                // Only react to changes *after* the initial state is processed
                if (!isInitialValue) {
                    if (activeKey != null && activeKey != _publicKey.value) {
                        _publicKey.value = activeKey // Update the ViewModel's public key state
                        hasLoadedInitialData = false // Reset flag to force reload for the new wallet
                        loadData(force = true) // Trigger data load for the new active wallet
                    } else if (activeKey == null && _publicKey.value.isNotEmpty()) {
                        // Handle case where all wallets might be deleted
                        Log.w("WalletViewModel", "Active wallet key became null.")
                        _publicKey.value = ""
                        _tokens.value = emptyList()
                        _tokenInfoMap.value = EMPTY_TOKEN_INFO_MAP
                        _balanceMap.value = EMPTY_BALANCE_MAP
                        _nftList.value = EMPTY_NFT_LIST
                        _displayedNftInfo.value = null
                        _isLoading.value = false
                        _isNftLoading.value = false
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

    fun setPreferredNft(nft: NftInfo) {
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
            val senderPublicKey = _publicKey.value
            if (senderPublicKey.isBlank()) {
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

    private fun loadDataIfNotLoaded() {
        if (!hasLoadedInitialData) {
            Log.d("WalletViewModel", "Initial data load triggered.")
            loadData(force = false)
        } else {
             Log.d("WalletViewModel", "Skipping initial data load, already loaded.")
             // Ensure loading state is false if we skip loading
             _isLoading.value = false

             _isNftLoading.value = false
        }
    }

    private fun loadData(force: Boolean) {
        // Only proceed if forced or if initial data hasn't been loaded
        if (!force && hasLoadedInitialData) {
            Log.d("WalletViewModel", "Skipping loadData, already loaded and not forced.")
            return
        }

        viewModelScope.launch {
            // Ensure we always fetch the token list for the *current* active wallet
            _tokens.value = walletManager.getTokenList().toList().sortedWith(compareBy<String> { it != "currency" }.thenBy { it })
            Log.d("WalletViewModel", "loadData: Updated token list for active wallet ${_publicKey.value}: ${_tokens.value}")

            Log.d("WalletViewModel", "Starting data fetch (force=$force)")

            _isLoading.value = true
            _isNftLoading.value = true // Assume NFTs load with other data for simplicity now

            // Check connectivity first
            _isCheckingConnection.value = true
            _isNodeConnected.value = networkService.checkNodeConnectivity()
            _isCheckingConnection.value = false
            Log.d("WalletViewModel", "Node connectivity check result: ${_isNodeConnected.value}")


            val currentTokens = _tokens.value
            val currentPublicKey = _publicKey.value
            val newTokenInfoMap = mutableMapOf<String, TokenInfo>()
            val newBalanceMap = mutableMapOf<String, Float>()

            // Fetch Tokens
            currentTokens.forEach { contract ->
                try {
                    val tokenInfo = networkService.getTokenInfo(contract)
                    newTokenInfoMap[contract] = tokenInfo
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
                val priceInfo = networkService.getXianPriceInfo()
                _xianPriceInfo.value = priceInfo
                _xianPrice.value = priceInfo?.let { (reserve0, reserve1) -> if (reserve1 != 0f) reserve0 / reserve1 else 0f }
                Log.d("WalletViewModel", "Fetched XIAN Price: ${_xianPrice.value} (Reserves: $priceInfo)")
            } catch (e: Exception) {
                 Log.e("WalletViewModel", "Error fetching XIAN price info", e)
            }


            // Fetch NFTs
            var fetchedNfts: List<NftInfo> = emptyList()
            if (currentPublicKey.isNotEmpty()) {
                try {
                    fetchedNfts = networkService.getNfts(currentPublicKey)
                    Log.d("WalletViewModel", "Fetched ${fetchedNfts.size} NFTs")
                } catch (e: Exception) {
                    Log.e("WalletViewModel", "Error fetching NFTs", e)
                }
            } else {
                Log.w("WalletViewModel", "Cannot fetch NFTs, publicKey is empty.")
            }
            _nftList.value = fetchedNfts

            // Determine displayed NFT (only on first load or refresh)
             if (!hasLoadedInitialData || force) {
                val preferredContract = walletManager.getPreferredNftContract()
                val newDisplayedNft = if (preferredContract != null) {
                    fetchedNfts.find { it.contractAddress == preferredContract } ?: fetchedNfts.firstOrNull()
                } else {
                    fetchedNfts.firstOrNull()
                }
                _displayedNftInfo.value = newDisplayedNft
                Log.d("WalletViewModel", "Updated displayed NFT: ${newDisplayedNft?.contractAddress}")
            }


            // Fetch initial stamp rate during data load
            try {
                 currentStampRate = networkService.getStampRate()
                 Log.d("WalletViewModel", "Fetched initial stamp rate: $currentStampRate")
            } catch (e: Exception) {
                 Log.e("WalletViewModel", "Failed to fetch initial stamp rate", e)
                 currentStampRate = null // Ensure it's null if fetch fails
            }

            _isLoading.value = false
            _isNftLoading.value = false
            hasLoadedInitialData = true // Mark initial load as complete
            Log.d("WalletViewModel", "Data fetch complete.")
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