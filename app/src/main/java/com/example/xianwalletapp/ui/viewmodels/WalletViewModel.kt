package com.example.xianwalletapp.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xianwalletapp.network.NftInfo
import com.example.xianwalletapp.network.TokenInfo
import com.example.xianwalletapp.network.XianNetworkService
import com.example.xianwalletapp.wallet.WalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Define default empty states
private val EMPTY_TOKEN_INFO_MAP: Map<String, TokenInfo> = emptyMap()
private val EMPTY_BALANCE_MAP: Map<String, Float> = emptyMap()
private val EMPTY_NFT_LIST: List<NftInfo> = emptyList()

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

    // Flag to prevent initial load if data already exists (e.g., ViewModel survived)
    private var hasLoadedInitialData = false

    init {
        // Initial data load trigger
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
}