package net.xian.xianwalletapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.network.TransactionResult
import net.xian.xianwalletapp.wallet.WalletManager
import org.json.JSONObject
import java.math.BigDecimal

data class StakingInfo(
    val beginTime: String? = null,
    val endTime: String? = null,
    val ratePerTokenPerSecond: Double = 0.0,
    val totalStaked: Double = 0.0,
    val stakeToken: String = "currency",
    val rewardToken: String = "currency",
    val userStaked: Double = 0.0,
    val userRewards: Double = 0.0,
    val apr: Double = 0.20, // 20% APR from contract
    val lockPeriod: String = "7 days"
)

data class StakingUiState(
    val stakingInfo: StakingInfo = StakingInfo(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isStaking: Boolean = false,
    val isUnstaking: Boolean = false,
    val isClaimingRewards: Boolean = false
)

class StakingViewModel(
    private val networkService: XianNetworkService,
    private val walletManager: WalletManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StakingUiState())
    val uiState: StateFlow<StakingUiState> = _uiState.asStateFlow()

    private val stakingContract = "con_staking_v1"

    init {
        loadStakingInfo()
    }

    fun loadStakingInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                val publicKey = walletManager.getPublicKey()
                if (publicKey == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Wallet not available"
                    )
                    return@launch
                }

                // Get staking info from contract
                val stakingInfo = getStakingInfoFromContract(publicKey)
                
                _uiState.value = _uiState.value.copy(
                    stakingInfo = stakingInfo,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error loading staking info: ${e.message}"
                )
            }
        }
    }

    private suspend fun getStakingInfoFromContract(publicKey: String): StakingInfo {
        try {
            // Call getInfo function with user address to get complete information
            val payload = JSONObject().apply {
                put("sender", publicKey)
                put("contract", stakingContract)
                put("function", "getInfo")
                put("kwargs", JSONObject().apply {
                    put("who", publicKey) // Pass user address to get user-specific data
                })
            }

            val result = networkService.simulateTransaction(payload)
            android.util.Log.d("StakingViewModel", "Raw result from contract: $result")
            
            if (result != null) {
                // The result comes as a string representation of an array
                val resultString = result.toString()
                android.util.Log.d("StakingViewModel", "Result string: $resultString")
                
                // Parse the string array format: "[value1, value2, ...]"
                if (resultString.startsWith("[") && resultString.endsWith("]")) {
                    val arrayContent = resultString.substring(1, resultString.length - 1)
                    val parts = arrayContent.split(", ")
                    
                    android.util.Log.d("StakingViewModel", "Array parts count: ${parts.size}, parts: $parts")
                    
                    if (parts.size >= 6) {
                        // Clean up the values (remove quotes from strings)
                        val beginTime = parts[0].trim()
                        val endTime = parts[1].trim()
                        val ratePerTokenPerSecond = parts[2].trim().toDoubleOrNull() ?: 0.0
                        val totalStaked = parts[3].trim().toDoubleOrNull() ?: 0.0
                        val stakeToken = parts[4].trim().replace("'", "")
                        val rewardToken = parts[5].trim().replace("'", "")
                        
                        // If we have user-specific data (8 elements total)
                        val userStaked = if (parts.size >= 7) {
                            parts[6].trim().toDoubleOrNull() ?: 0.0
                        } else {
                            0.0
                        }
                        
                        val userRewards = if (parts.size >= 8) {
                            parts[7].trim().toDoubleOrNull() ?: 0.0
                        } else {
                            0.0
                        }
                        
                        android.util.Log.d("StakingViewModel", "Parsed values - beginTime: $beginTime, endTime: $endTime, rate: $ratePerTokenPerSecond, totalStaked: $totalStaked, stakeToken: $stakeToken, rewardToken: $rewardToken, userStaked: $userStaked, userRewards: $userRewards")
                        
                        return StakingInfo(
                            beginTime = beginTime,
                            endTime = endTime,
                            ratePerTokenPerSecond = ratePerTokenPerSecond,
                            totalStaked = totalStaked,
                            stakeToken = stakeToken,
                            rewardToken = rewardToken,
                            userStaked = userStaked,
                            userRewards = userRewards
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StakingViewModel", "Error getting staking info: ${e.message}", e)
        }

        // Return default values if contract call fails
        return StakingInfo()
    }

    fun stake(amount: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isStaking = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val amountDecimal = BigDecimal(amount)
                if (amountDecimal <= BigDecimal.ZERO) {
                    throw IllegalArgumentException("Amount must be greater than 0")
                }

                val publicKey = walletManager.getPublicKey()
                if (publicKey == null) {
                    throw IllegalArgumentException("Wallet not available")
                }

                // Get the private key using the same pattern as other screens
                val needsPasswordInput = walletManager.getUnlockedPrivateKey() == null
                var privateKey: ByteArray? = null
                
                if (needsPasswordInput) {
                    if (password.isEmpty()) {
                        throw IllegalArgumentException("Password is required")
                    }
                    privateKey = walletManager.unlockWallet(password)
                    if (privateKey == null) {
                        throw IllegalArgumentException("Invalid password")
                    }
                    android.util.Log.d("StakingViewModel", "Wallet unlocked successfully for stake transaction.")
                } else {
                    privateKey = walletManager.getUnlockedPrivateKey()
                    if (privateKey == null) {
                        throw IllegalArgumentException("Wallet became locked. Please try again.")
                    }
                    android.util.Log.d("StakingViewModel", "Using cached key for stake transaction.")
                }

                android.util.Log.d("StakingViewModel", "Starting staking process for amount: $amount")

                // Step 1: Approve tokens for the staking contract
                android.util.Log.d("StakingViewModel", "Step 1: Approving tokens for staking contract")
                
                val approveKwargs = JSONObject().apply {
                    put("amount", amountDecimal.toDouble())
                    put("to", stakingContract) // Approve the staking contract to spend our tokens
                }

                val approveResult = networkService.sendTransaction(
                    contract = "currency", // The token contract (XIAN)
                    method = "approve",
                    kwargs = approveKwargs,
                    privateKey = privateKey,
                    stampLimit = 50000
                )

                if (!approveResult.success) {
                    throw Exception("Failed to approve tokens: ${approveResult.errors}")
                }

                android.util.Log.d("StakingViewModel", "Token approval successful: ${approveResult.txHash}")

                // Wait a moment for the approval to be processed
                kotlinx.coroutines.delay(2000)

                // Step 2: Deposit tokens to staking contract
                android.util.Log.d("StakingViewModel", "Step 2: Depositing tokens to staking contract")
                
                val depositKwargs = JSONObject().apply {
                    put("amount", amountDecimal.toDouble())
                }

                val depositResult = networkService.sendTransaction(
                    contract = stakingContract,
                    method = "deposit",
                    kwargs = depositKwargs,
                    privateKey = privateKey,
                    stampLimit = 100000
                )

                if (depositResult.success) {
                    _uiState.value = _uiState.value.copy(
                        isStaking = false,
                        successMessage = "Staking completed successfully!"
                    )
                    // Reload staking info after successful stake
                    loadStakingInfo()
                } else {
                    throw Exception("Deposit failed: ${depositResult.errors}")
                }

            } catch (e: Exception) {
                android.util.Log.e("StakingViewModel", "Staking failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isStaking = false,
                    errorMessage = "Staking failed: ${e.message}"
                )
            }
        }
    }

    fun unstake(amount: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUnstaking = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val amountDecimal = BigDecimal(amount)
                if (amountDecimal <= BigDecimal.ZERO) {
                    throw IllegalArgumentException("Amount must be greater than 0")
                }

                val publicKey = walletManager.getPublicKey()
                if (publicKey == null) {
                    throw IllegalArgumentException("Wallet not available")
                }

                // Get the private key using the same pattern as other screens
                val needsPasswordInput = walletManager.getUnlockedPrivateKey() == null
                var privateKey: ByteArray? = null
                
                if (needsPasswordInput) {
                    if (password.isEmpty()) {
                        throw IllegalArgumentException("Password is required")
                    }
                    privateKey = walletManager.unlockWallet(password)
                    if (privateKey == null) {
                        throw IllegalArgumentException("Invalid password")
                    }
                    android.util.Log.d("StakingViewModel", "Wallet unlocked successfully for unstake transaction.")
                } else {
                    privateKey = walletManager.getUnlockedPrivateKey()
                    if (privateKey == null) {
                        throw IllegalArgumentException("Wallet became locked. Please try again.")
                    }
                    android.util.Log.d("StakingViewModel", "Using cached key for unstake transaction.")
                }

                android.util.Log.d("StakingViewModel", "Starting unstaking process for amount: $amount")

                // Create transaction to unstake tokens
                val kwargs = JSONObject().apply {
                    put("amount", amountDecimal.toDouble())
                }

                val unstakeResult = networkService.sendTransaction(
                    contract = stakingContract,
                    method = "withdraw",
                    kwargs = kwargs,
                    privateKey = privateKey,
                    stampLimit = 100000
                )

                if (unstakeResult.success) {
                    _uiState.value = _uiState.value.copy(
                        isUnstaking = false,
                        successMessage = "Unstaking transaction submitted successfully!"
                    )
                    // Reload staking info after successful unstake
                    loadStakingInfo()
                } else {
                    throw Exception("Unstaking failed: ${unstakeResult.errors}")
                }

            } catch (e: Exception) {
                android.util.Log.e("StakingViewModel", "Unstaking failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isUnstaking = false,
                    errorMessage = "Unstaking failed: ${e.message}"
                )
            }
        }
    }

    fun claimRewards(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isClaimingRewards = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val publicKey = walletManager.getPublicKey()
                if (publicKey == null) {
                    throw IllegalArgumentException("Wallet not available")
                }

                // Get the private key using the same pattern as other screens
                val needsPasswordInput = walletManager.getUnlockedPrivateKey() == null
                var privateKey: ByteArray? = null
                
                if (needsPasswordInput) {
                    if (password.isEmpty()) {
                        throw IllegalArgumentException("Password is required")
                    }
                    privateKey = walletManager.unlockWallet(password)
                    if (privateKey == null) {
                        throw IllegalArgumentException("Invalid password")
                    }
                    android.util.Log.d("StakingViewModel", "Wallet unlocked successfully for claim rewards transaction.")
                } else {
                    privateKey = walletManager.getUnlockedPrivateKey()
                    if (privateKey == null) {
                        throw IllegalArgumentException("Wallet became locked. Please try again.")
                    }
                    android.util.Log.d("StakingViewModel", "Using cached key for claim rewards transaction.")
                }

                // Get current rewards amount first
                val currentRewards = _uiState.value.stakingInfo.userRewards
                if (currentRewards <= 0) {
                    throw IllegalArgumentException("No rewards to claim")
                }

                android.util.Log.d("StakingViewModel", "Claiming rewards amount: $currentRewards")

                // Create transaction to claim rewards
                val kwargs = JSONObject().apply {
                    put("amount", currentRewards)
                }

                val claimResult = networkService.sendTransaction(
                    contract = stakingContract,
                    method = "withdrawRewards",
                    kwargs = kwargs,
                    privateKey = privateKey,
                    stampLimit = 100000
                )

                if (claimResult.success) {
                    _uiState.value = _uiState.value.copy(
                        isClaimingRewards = false,
                        successMessage = "Rewards claimed successfully!"
                    )
                    // Reload staking info after successful claim
                    loadStakingInfo()
                } else {
                    throw Exception("Claiming rewards failed: ${claimResult.errors}")
                }

            } catch (e: Exception) {
                android.util.Log.e("StakingViewModel", "Claiming rewards failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isClaimingRewards = false,
                    errorMessage = "Claiming rewards failed: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}