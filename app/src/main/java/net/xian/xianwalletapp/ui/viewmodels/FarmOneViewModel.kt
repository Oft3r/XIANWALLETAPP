package net.xian.xianwalletapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.xian.xianwalletapp.network.XianNetworkService
import net.xian.xianwalletapp.wallet.WalletManager
import org.json.JSONException
import org.json.JSONObject

/**
 * ViewModel dedicado a leer la info del Farm ID 1 del contrato con_multi_farmv1
 * Solo lectura (no acciones de deposit/withdraw aquí todavía).
 */

data class FarmOneInfo(
    val farmId: Int = 1,
    val beginTime: String? = null,
    val endTime: String? = null,
    val durationDays: Int? = null,
    val rps: Double = 0.0,
    val totalStaked: Double = 0.0,
    val depositToken: String = "",
    val rewardToken: String = "",
    val creator: String = "",
    val budget: Double = 0.0,
    val paidToUsers: Double = 0.0,
    val remainingAvailable: Double = 0.0,
    val suspicious: Boolean = false,
    val verified: Boolean = false,
    val ended: Boolean = false,
    val userStaked: Double = 0.0,
    val userSavedRewards: Double = 0.0,
    val userPendingRewards: Double = 0.0,
    val userTotalRewards: Double = 0.0,
    val referralRewardsAvailable: Double = 0.0,
    val userTotalDeposits: Double = 0.0
)

data class FarmOneUiState(
    val info: FarmOneInfo = FarmOneInfo(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDepositing: Boolean = false,
    val isWithdrawing: Boolean = false,
    val isClaiming: Boolean = false
)

class FarmOneViewModel(
    private val networkService: XianNetworkService,
    private val walletManager: WalletManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FarmOneUiState())
    val uiState: StateFlow<FarmOneUiState> = _uiState.asStateFlow()

    private val contractName = "con_multi_farmv1"
    private val farmId = 1

    init { loadFarmInfo() }

    fun loadFarmInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val publicKey = walletManager.getPublicKey()
                if (publicKey == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Wallet no disponible")
                    return@launch
                }

                val farmInfo = fetchFarmInfo(publicKey)
                _uiState.value = _uiState.value.copy(info = farmInfo, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Error desconocido")
            }
        }
    }

    private suspend fun fetchFarmInfo(user: String): FarmOneInfo {
        // Helper to extract the 'result' field (python-like dict string) from simulateTransaction output
        fun extractResultString(anyResult: Any?): String? {
            if (anyResult == null) return null
            val raw = anyResult.toString()
            // Fast path: looks like python dict from contract directly
            if (raw.startsWith("{") && raw.contains("'staked'")) {
                android.util.Log.d("FarmOneViewModel", "extractResultString: detected direct python dict string")
                return raw
            }
            return try {
                val outer = JSONObject(raw)
                val inner = outer.optString("result", null)
                if (inner == null || inner == "null") {
                    // Maybe the raw itself was the dict array or something else, fallback
                    raw
                } else inner
            } catch (e: Exception) {
                android.util.Log.d("FarmOneViewModel", "extractResultString: not JSON, returning raw: ${e.message}")
                raw
            }
        }

        // Robust manual parser for simple python-like dicts returned by the contract.
        // Expected patterns (examples):
        // {'begtime': 2025-08-13 20:28:40, 'suspicious': False, 'rps': 0.005, 'total_staked': 500E+3}
        fun pythonDictStringToJsonObject(python: String?): JSONObject? {
            if (python.isNullOrBlank()) return null
            val original = python
            return try {
                var s = python.trim()
                // Strip wrapping quotes if any
                if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                    s = s.substring(1, s.length - 1)
                }
                if (s.startsWith("{")) s = s.substring(1)
                if (s.endsWith("}")) s = s.substring(0, s.length - 1)
                // Remove trailing comma if present
                s = s.trim().trimEnd(',')

                val map = mutableMapOf<String, Any?>()
                var i = 0
                fun skipWhitespace() { while (i < s.length && s[i].isWhitespace()) i++ }
                val dateTimeRegex = Regex("""^\n?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})$""")
                while (i < s.length) {
                    skipWhitespace()
                    if (i >= s.length) break
                    // Parse key (expect quotes ' or ")
                    if (s[i] != '\'' && s[i] != '"') {
                        // Unexpected format; abort with log
                        android.util.Log.w("FarmOneViewModel", "Parser expected quote at pos=$i in: $s")
                        break
                    }
                    val quote = s[i]
                    i++
                    val keyStart = i
                    while (i < s.length && s[i] != quote) i++
                    if (i >= s.length) break
                    val key = s.substring(keyStart, i)
                    i++ // consume quote
                    skipWhitespace()
                    if (i >= s.length || s[i] != ':') {
                        android.util.Log.w("FarmOneViewModel", "Parser expected ':' after key=$key in: $s")
                        break
                    }
                    i++ // consume ':'
                    skipWhitespace()
                    // Parse value until comma at top level
                    val valStart = i
                    var inQuotes = false
                    var quoteChar = '\''
                    while (i < s.length) {
                        val c = s[i]
                        if (!inQuotes && c == ',') break
                        if (!inQuotes && c == '}') break
                        if (c == '\'' || c == '"') {
                            if (!inQuotes) {
                                inQuotes = true; quoteChar = c
                            } else if (quoteChar == c) {
                                inQuotes = false
                            }
                        }
                        i++
                    }
                    var rawVal = s.substring(valStart, i).trim()
                    // Advance past comma if present
                    if (i < s.length && s[i] == ',') i++
                    // Normalize value
                    val value: Any? = when {
                        rawVal.equals("True", ignoreCase = true) -> true
                        rawVal.equals("False", ignoreCase = true) -> false
                        rawVal.equals("None", ignoreCase = true) -> null
                        // Quoted string
                        (rawVal.startsWith("\"") && rawVal.endsWith("\"")) || (rawVal.startsWith("'") && rawVal.endsWith("'")) -> rawVal.substring(1, rawVal.length - 1)
                        // DateTime without quotes
                        dateTimeRegex.matches(rawVal) -> rawVal
                        // Numeric (handle scientific 500E+3)
                        else -> {
                            val norm = rawVal.replace("E+", "e+")
                            norm.toDoubleOrNull() ?: rawVal // fallback keep as string
                        }
                    }
                    map[key] = value
                }
                // Post-process: convert unquoted datetime strings (YYYY-MM-DD HH:MM:SS) to string explicitly (already stored as String)
                val json = JSONObject()
                for ((k, v) in map) {
                    when (v) {
                        null -> json.put(k, JSONObject.NULL)
                        is Boolean, is Number, is JSONObject -> json.put(k, v)
                        else -> json.put(k, v.toString())
                    }
                }
                json
            } catch (e: Exception) {
                android.util.Log.e("FarmOneViewModel", "Manual parse failed. original='$original' msg=${e.message}")
                null
            }
        }

        // 1. getFarmInfo(farm_id)
        val getFarmInfoPayload = JSONObject().apply {
            put("sender", user)
            put("contract", contractName)
            put("function", "getFarmInfo")
            put("kwargs", JSONObject().apply { put("farm_id", farmId) })
        }
        val farmInfoResult = networkService.simulateTransaction(getFarmInfoPayload)
        val farmInfoPythonString = extractResultString(farmInfoResult)
    android.util.Log.d("FarmOneViewModel", "farmInfo raw result: $farmInfoPythonString")
        val farmJson = pythonDictStringToJsonObject(farmInfoPythonString)

        var beginTime: String? = null
        var endTime: String? = null
        var durationDays: Int? = null
        var rps = 0.0
        var totalStaked = 0.0
        var depositToken = ""
        var rewardToken = ""
        var creator = ""
        var budget = 0.0
        var paidToUsers = 0.0
        var remainingAvailable = 0.0
        var suspicious = false
        var verified = false
        var ended = false

        if (farmJson != null) {
            beginTime = farmJson.optString("begtime", null)
            endTime = farmJson.optString("endtime", null)
            durationDays = if (farmJson.has("duration_days")) farmJson.optInt("duration_days") else null
            rps = farmJson.optDouble("rps", 0.0)
            totalStaked = farmJson.optDouble("total_staked", 0.0)
            depositToken = farmJson.optString("deposit_token", "")
            rewardToken = farmJson.optString("reward_token", "")
            creator = farmJson.optString("creator", "")
            budget = farmJson.optDouble("budget", 0.0)
            paidToUsers = farmJson.optDouble("paid_to_users", 0.0)
            remainingAvailable = farmJson.optDouble("remaining_available", 0.0)
            suspicious = farmJson.optBoolean("suspicious", false)
            verified = farmJson.optBoolean("verified", false)
            ended = farmJson.optBoolean("ended", false)
        }

        // 2. getUserInfo(farm_id, who)
        val getUserInfoPayload = JSONObject().apply {
            put("sender", user)
            put("contract", contractName)
            put("function", "getUserInfo")
            put("kwargs", JSONObject().apply {
                put("farm_id", farmId)
                put("who", user)
            })
        }
        val userInfoResult = networkService.simulateTransaction(getUserInfoPayload)
        // If simulateTransaction already returned the python dict string, keep it
        val userInfoPythonString = when (userInfoResult) {
            is String -> if (userInfoResult.startsWith("{") && userInfoResult.contains("'staked'")) userInfoResult else extractResultString(userInfoResult)
            else -> extractResultString(userInfoResult)
        }
        android.util.Log.d("FarmOneViewModel", "userInfo raw result: $userInfoPythonString (orig=${userInfoResult?.toString()?.take(80)})")
        val userJson = pythonDictStringToJsonObject(userInfoPythonString)

    var userStaked = 0.0
    var userSavedRewards = 0.0
    var userPendingRewards = 0.0
    var userTotalRewards = 0.0
    var referralRewardsAvailable = 0.0
    var userTotalDeposits = 0.0

        if (userJson != null) {
            // Keys from contract user dict
            // 'staked', 'saved_rewards', 'pending_rewards', 'total_rewards'
            // numeric values may be scientific notation like 500E+3
            fun parseSci(key: String): Double {
                val v = userJson.optString(key, "0")
                return try { v.replace("E+", "e+").toDouble() } catch (e: Exception) { 0.0 }
            }
            userStaked = parseSci("staked")
            userSavedRewards = parseSci("saved_rewards")
            userPendingRewards = parseSci("pending_rewards")
            userTotalRewards = parseSci("total_rewards")
                referralRewardsAvailable = parseSci("referral_rewards_available")
                userTotalDeposits = parseSci("total_deposits")
            // Business rule per user: show "Staked" as total_deposits if staked is 0 but total deposits present
            if (userStaked == 0.0 && userTotalDeposits > 0.0) {
                userStaked = userTotalDeposits
            }
        }

        return FarmOneInfo(
            beginTime = beginTime,
            endTime = endTime,
            durationDays = durationDays,
            rps = rps,
            totalStaked = totalStaked,
            depositToken = depositToken,
            rewardToken = rewardToken,
            creator = creator,
            budget = budget,
            paidToUsers = paidToUsers,
            remainingAvailable = remainingAvailable,
            suspicious = suspicious,
            verified = verified,
            ended = ended,
            userStaked = userStaked,
            userSavedRewards = userSavedRewards,
            userPendingRewards = userPendingRewards,
            userTotalRewards = userTotalRewards
            , referralRewardsAvailable = referralRewardsAvailable
            , userTotalDeposits = userTotalDeposits
        )
    }

    private suspend fun submitTransaction(function: String, kwargs: JSONObject, password: String?): Boolean {
        return try {
            var privateKey = walletManager.getUnlockedPrivateKey()
            if (privateKey == null) {
                if (password.isNullOrBlank()) return false
                privateKey = walletManager.unlockWallet(password)
            }
            if (privateKey == null) return false
            val result = networkService.sendTransaction(
                contract = contractName,
                method = function,
                kwargs = kwargs,
                privateKey = privateKey,
                stampLimit = 0 // let service estimate
            )
            if (!result.success) {
                _uiState.value = _uiState.value.copy(error = result.errors)
            }
            result.success
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message ?: "Tx error")
            false
        }
    }

    fun deposit(amount: Double, password: String?) {
        if (amount <= 0) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDepositing = true, error = null)
            val success = submitTransaction(
                function = "deposit",
                kwargs = JSONObject().apply {
                    put("farm_id", farmId)
                    put("amount", amount)
                },
                password = password
            )
            _uiState.value = _uiState.value.copy(isDepositing = false)
            if (success) loadFarmInfo()
        }
    }

    // Approve XWT token for farm contract and then deposit
    fun approveAndDeposit(amount: Double, password: String?) {
        if (amount <= 0) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDepositing = true, error = null)
            try {
                var privateKey = walletManager.getUnlockedPrivateKey()
                if (privateKey == null) {
                    if (password.isNullOrBlank()) throw IllegalArgumentException("Password required")
                    privateKey = walletManager.unlockWallet(password)
                }
                if (privateKey == null) throw IllegalStateException("Could not unlock wallet")

                // 1. Approve con_multi_farmv1 to spend amount of con_xwt
                val approveResult = networkService.sendTransaction(
                    contract = "con_xwt",
                    method = "approve",
                    kwargs = JSONObject().apply {
                        put("amount", amount)
                        put("to", contractName)
                    },
                    privateKey = privateKey,
                    stampLimit = 50000
                )
                if (!approveResult.success) {
                    _uiState.value = _uiState.value.copy(isDepositing = false, error = approveResult.errors)
                    return@launch
                }

                // Brief delay to allow state update
                kotlinx.coroutines.delay(1500)

                // 2. Deposit to farm
                val depositResult = networkService.sendTransaction(
                    contract = contractName,
                    method = "deposit",
                    kwargs = JSONObject().apply {
                        put("farm_id", farmId)
                        put("amount", amount)
                    },
                    privateKey = privateKey,
                    stampLimit = 100000
                )
                _uiState.value = _uiState.value.copy(isDepositing = false, error = if (!depositResult.success) depositResult.errors else null)
                if (depositResult.success) loadFarmInfo()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDepositing = false, error = e.message)
            }
        }
    }

    fun withdraw(amount: Double, password: String?) {
        if (amount <= 0) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWithdrawing = true, error = null)
            val success = submitTransaction(
                function = "withdraw",
                kwargs = JSONObject().apply {
                    put("farm_id", farmId)
                    put("amount", amount)
                },
                password = password
            )
            _uiState.value = _uiState.value.copy(isWithdrawing = false)
            if (success) loadFarmInfo()
        }
    }

    fun claimRewards(password: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClaiming = true, error = null)
            val success = submitTransaction(
                function = "withdrawRewards",
                kwargs = JSONObject().apply { put("farm_id", farmId) },
                password = password
            )
            _uiState.value = _uiState.value.copy(isClaiming = false)
            if (success) loadFarmInfo()
        }
    }
}

class FarmOneViewModelFactory(
    private val networkService: XianNetworkService,
    private val walletManager: WalletManager
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FarmOneViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FarmOneViewModel(networkService, walletManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
