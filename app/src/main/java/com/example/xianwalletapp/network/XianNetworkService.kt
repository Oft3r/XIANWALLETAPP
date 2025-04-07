package com.example.xianwalletapp.network

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull // Import for extension function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import com.example.xianwalletapp.crypto.XianCrypto
import com.example.xianwalletapp.wallet.WalletManager
import com.google.gson.reflect.TypeToken // Needed for parsing list of arguments
/**
 * Service class for interacting with the Xian Network
 * Handles all network calls to the Xian RPC endpoints
 */
class XianNetworkService private constructor(private val context: Context) {
    companion object {
        // Singleton instance
        @Volatile
        private var instance: XianNetworkService? = null

        // Define the XNS contract name here
        private const val XNS_CONTRACT_NAME = "con_name_service_final"
        
        fun getInstance(context: Context): XianNetworkService {
            return instance ?: synchronized(this) {
                instance ?: XianNetworkService(context).also { instance = it }
            }
        }
    }
    
    // Referencia al WalletManager con contexto
    private val walletManager = WalletManager.getInstance(context)
    
    private fun deriveAddressFromPublicKey(publicKey: String): String {
        try {
            // Log the input public key for debugging
            android.util.Log.d("XianNetworkService", "Deriving address from public key: $publicKey")
            
            // Important: In Xian network, the address IS the public key
            // This matches the behavior in xian.js where the publicKey is used directly
            return publicKey
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error deriving address: ${e.message}", e)
            return publicKey // Return public key if derivation fails
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    
    // Lista de nodos alternativos para probar si el principal falla
    private val nodeUrls = listOf(
        "https://node.xian.org"
    )
    
    private var rpcUrl = nodeUrls[0] // Comenzar con el nodo principal
    private var explorerUrl = "https://explorer.xian.org"
    private var chainId: String? = null
    
    /**
     * Set the RPC URL for the Xian network
     */
    fun setRpcUrl(url: String) {
        rpcUrl = url
    }
    
    /**
     * Set the explorer URL for the Xian network
     */
    fun setExplorerUrl(url: String) {
        explorerUrl = url
    }

    // Variable para ayudar a depurar problemas de URL
    private fun logURL(url: String, message: String) {
        android.util.Log.d("XianNetworkService", "$message: $url")
        // Also show bytes for debugging
        val bytes = url.toByteArray()
        val hexString = bytes.joinToString("") { "%02x".format(it) }
        android.util.Log.d("XianNetworkService", "URL bytes: $hexString")
    }

    /**
     * Prueba conectividad con múltiples nodos y selecciona el primero que responda
     * @return true si se encontró un nodo funcional, false si todos fallan
     */
    suspend fun findWorkingNode(): Boolean = withContext(Dispatchers.IO) {
        var nodeFound = false
        
        for (nodeUrl in nodeUrls) {
            try {
                android.util.Log.d("XianNetworkService", "Testing connection to node: $nodeUrl")
                
                val response = client.newCall(
                    Request.Builder()
                        .url("$nodeUrl/status")
                        .build()
                ).execute()
                
                if (response.isSuccessful) {
                    android.util.Log.d("XianNetworkService", "Nodo funcional encontrado: $nodeUrl")
                    rpcUrl = nodeUrl
                    nodeFound = true
                    break
                } else {
                    android.util.Log.d("XianNetworkService", "Node $nodeUrl responded with code: ${response.code}")
                }
            } catch (e: Exception) {
                android.util.Log.e("XianNetworkService", "Error al conectar con el nodo $nodeUrl: ${e.message}")
            }
        }
        
        return@withContext nodeFound
    }

    /**
     * Verifica la conectividad con el nodo Xian
     * @return true si se puede conectar, false en caso contrario
     */
    suspend fun checkNodeConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Intentar primero con el nodo configurado actualmente
            val response = client.newCall(
                Request.Builder()
                    .url("$rpcUrl/status")
                    .build()
            ).execute()
            
            val isConnected = response.isSuccessful
            android.util.Log.d("XianNetworkService", "Connection with node $rpcUrl: $isConnected, code: ${response.code}")
            
            if (isConnected) {
                return@withContext true
            }
            
            // Si falla, intentar encontrar un nodo alternativo
            return@withContext findWorkingNode()
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error al conectar con el nodo $rpcUrl: ${e.message}")
            
            // Si hay error, intentar buscar un nodo alternativo
            return@withContext findWorkingNode()
        }
    }

    /**
     * Get the next nonce for an address
     */
    suspend fun getNonce(address: String): Int = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder()
                    .url("$rpcUrl/abci_query?path=\"/get_next_nonce/$address\"")
                    .build()
            ).execute()

            val json = JSONObject(response.body?.string() ?: "{}")
            val base64Value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (base64Value == "AA==") return@withContext 0
            
            return@withContext if (base64Value != null) {
                String(Base64.decode(base64Value, Base64.DEFAULT)).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting nonce: ${e.message}")
            return@withContext 0
        }
    }

    /**
     * Get the balance of a token for a specific address using public key
     * Uses the same approach as the web wallet:
     * 1. First tries to use simulate_tx with balance_of function
     * 2. Falls back to direct variable access if the first method fails
     * 
     * @param contract The token contract address
     * @param publicKey The public key of the wallet to check balance for
     * @return The token balance as a Float
     */
    suspend fun getTokenBalance(contract: String, publicKey: String): Float = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node. Aborting balance retrieval.")
                return@withContext 0f
            }
            
            android.util.Log.d("XianNetworkService", "Usando nodo: $rpcUrl para obtener balance")
            
            // In Xian, the address and the public key are the same
            val address = publicKey
            android.util.Log.d("XianNetworkService", "Getting balance for contract: $contract, address: $address")
            
            // METHOD 1: Use simulate_tx with the balance_of function
            // Create payload exactly as in the web version
            val payload = JSONObject().apply {
                put("sender", address)
                put("contract", contract)
                put("function", "balance_of")
                put("kwargs", JSONObject().apply {
                    put("address", address)
                })
            }
            
            // Convert payload to hex string exactly as in the web version
            val payloadBytes = payload.toString().toByteArray()
            val payloadHex = payloadBytes.joinToString("") { "%02x".format(it) }
            
            // It's crucial to use exactly the same URL format as in the web version
            // In the web version, URLs are constructed like this:
            // fetch(RPC + '/abci_query?path="/simulate_tx/' + hex + '"')
            val unescapedUrl = "$rpcUrl/abci_query?path=\"/simulate_tx/$payloadHex\""
            // Usamos java.net.URLEncoder para asegurarnos de que las comillas dobles se codifiquen correctamente
            // pero preservamos la forma /abci_query?path= sin codificar
            val baseUrl = "$rpcUrl/abci_query?path="
            val pathPart = "\"/simulate_tx/$payloadHex\""
            val encodedPathPart = java.net.URLEncoder.encode(pathPart, "UTF-8")
            // URLEncoder codifica los espacios como '+', los reemplazamos por %20
            val finalPathPart = encodedPathPart.replace("+", "%20")
            val simulateUrl = baseUrl + finalPathPart
            
            logURL(unescapedUrl, "URL sin escapar")
            logURL(simulateUrl, "URL final para simulate_tx")
            
            // Realizar la solicitud simulate_tx
            val simulateResponse = client.newCall(
                Request.Builder()
                    .url(simulateUrl)
                    .build()
            ).execute()
            
            val simulateResponseBody = simulateResponse.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Simulate response: $simulateResponseBody")
            
            val simulateJson = JSONObject(simulateResponseBody)
            val simulateValue = simulateJson.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            android.util.Log.d("XianNetworkService", "Simulate value (Base64): $simulateValue")
            
            if (simulateValue != null && simulateValue.isNotEmpty()) {
                try {
                    // Decodificar el valor Base64
                    val simulateDecodedBytes = Base64.decode(simulateValue, Base64.DEFAULT)
                    val simulateDecoded = String(simulateDecodedBytes)
                    android.util.Log.d("XianNetworkService", "Simulate decoded: $simulateDecoded")
                    
                    // Verify decoded value following the exact logic of the web version
                    // Redundant null check removed for simulateDecoded as isNotEmpty() implies not null
                    if (simulateDecoded != "ée" && simulateDecoded != "AA==" && simulateDecoded.isNotEmpty() && simulateDecoded != "null") {
                        try {
                            // In the web version, the "result" field is extracted directly from the JSON
                            val decodedJson = JSONObject(simulateDecoded)
                            if (decodedJson.has("result")) {
                                val result = decodedJson.getString("result")
                                android.util.Log.d("XianNetworkService", "Result from JSON: $result")
                                
                                // Use BigDecimal to avoid precision issues with decimal numbers
                                val parsedBalance = try {
                                    java.math.BigDecimal(result)
                                } catch (e: Exception) {
                                    java.math.BigDecimal.ZERO
                                }
                                
                                // Formatear a 1 decimal en lugar de 8
                                val formattedBalance = parsedBalance.setScale(1, java.math.RoundingMode.HALF_UP).toFloat()
                                android.util.Log.d("XianNetworkService", "Final parsed balance from simulate: $formattedBalance")
                                return@withContext formattedBalance
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("XianNetworkService", "Error parsing simulate JSON: ${e.message}")
                            // Continuar con el fallback si hay error de parseo
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XianNetworkService", "Error decodificando Base64: ${e.message}")
                }
            }
            
            // METHOD 2 (FALLBACK): Use getVariable as in the web version
            android.util.Log.d("XianNetworkService", "Using getVariable fallback method")
            
            // Build the URL for getVariable exactly as in the web version
            val unescapedVarUrl = "$rpcUrl/abci_query?path=\"/get/$contract.balances:$address\""
            val varPathPart = "\"/get/$contract.balances:$address\""
            val encodedVarPathPart = java.net.URLEncoder.encode(varPathPart, "UTF-8")
            val finalVarPathPart = encodedVarPathPart.replace("+", "%20")
            val variableUrl = baseUrl + finalVarPathPart
            
            logURL(unescapedVarUrl, "URL sin escapar para getVariable")
            logURL(variableUrl, "URL final para getVariable")
            
            val variableResponse = client.newCall(
                Request.Builder()
                    .url(variableUrl)
                    .build()
            ).execute()
            
            val variableResponseBody = variableResponse.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Variable response: $variableResponseBody")
            
            val variableJson = JSONObject(variableResponseBody)
            val variableValue = variableJson.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            android.util.Log.d("XianNetworkService", "Variable value (Base64): $variableValue")
            
            // Check if the value is null or AA== (empty) as in the web version
            if (variableValue == null || variableValue == "AA==") {
                return@withContext 0f
            }
            
            try {
                // Decodificar el valor Base64
                val variableDecodedBytes = Base64.decode(variableValue, Base64.DEFAULT)
                val variableDecoded = String(variableDecodedBytes)
                android.util.Log.d("XianNetworkService", "Variable decoded: $variableDecoded")
                
                // Try to parse the value as a number
                val parsedBalance = try {
                    java.math.BigDecimal(variableDecoded)
                } catch (e: Exception) {
                    java.math.BigDecimal.ZERO
                }
                
                // Formatear a 1 decimal en lugar de 8
                val formattedBalance = parsedBalance.setScale(1, java.math.RoundingMode.HALF_UP).toFloat()
                android.util.Log.d("XianNetworkService", "Final parsed balance from variable: $formattedBalance")
                return@withContext formattedBalance
            } catch (e: Exception) {
                android.util.Log.e("XianNetworkService", "Error parsing variable value: ${e.message}")
                return@withContext 0f
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting token balance: ${e.message}")
            return@withContext 0f
        }
    }
    
    /**
     * Helper function to fetch a variable using abci_query, similar to web wallet's getVariable
     */
    private suspend fun fetchVariable(contract: String, variablePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = "$rpcUrl/abci_query?path="
            // Construct the path exactly like the web wallet: "/get/contract.variable:key"
            val pathPart = "\"/get/$contract.$variablePath\""
            val encodedPathPart = java.net.URLEncoder.encode(pathPart, "UTF-8")
            val finalPathPart = encodedPathPart.replace("+", "%20") // URL Encode spaces correctly
            val queryUrl = baseUrl + finalPathPart

            logURL(queryUrl, "URL for fetchVariable ($variablePath)")

            val response = client.newCall(
                Request.Builder()
                    .url(queryUrl)
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                android.util.Log.w("XianNetworkService", "Failed to fetch variable $variablePath for $contract: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)
            val base64Value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")

            // Check for null or empty ("AA==") response, same as web wallet
            if (base64Value == null || base64Value == "AA==") {
                android.util.Log.d("XianNetworkService", "Variable $variablePath for $contract is null or empty.")
                return@withContext null
            }

            // Decode Base64
            return@withContext try {
                val decodedBytes = Base64.decode(base64Value, Base64.DEFAULT)
                String(decodedBytes)
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("XianNetworkService", "Failed to decode Base64 for $variablePath: $base64Value", e)
                null
            }

        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error fetching variable $variablePath for $contract: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Get token information for a specific contract using metadata queries.
     * Replicates the logic from the xian-web-wallet.
     *
     * @param contract The token contract address
     * @return TokenInfo object with token details
     */
     suspend fun getTokenInfo(contract: String): TokenInfo = withContext(Dispatchers.IO) {
        // Handle native currency special case
        if (contract == "currency") {
            return@withContext TokenInfo(name = "Xian", symbol = "XIAN", contract = contract)
        }

        try {
            // Check node connectivity first
            if (!checkNodeConnectivity()) {
                android.util.Log.e("XianNetworkService", "No node connection for getTokenInfo. Returning defaults.")
                // Return defaults using contract address if no connection
                return@withContext TokenInfo(name = contract, symbol = contract.take(3).uppercase(), contract = contract)
            }

            // Fetch metadata using the helper function
            val tokenName = fetchVariable(contract, "metadata:token_name")
            val tokenSymbol = fetchVariable(contract, "metadata:token_symbol")
            val logoUrl = fetchVariable(contract, "metadata:token_logo_url")
            // TODO: Optionally fetch decimals if needed: fetchVariable(contract, "metadata:decimals")

            android.util.Log.d("XianNetworkService", "Fetched for $contract: Name=$tokenName, Symbol=$tokenSymbol, Logo=$logoUrl")

            // Determine final values, using contract address as fallback ONLY if metadata is missing
            val finalName = tokenName ?: contract
            val finalSymbol = tokenSymbol ?: contract.take(3).uppercase()

            return@withContext TokenInfo(
                name = finalName,
                symbol = finalSymbol,
                contract = contract,
                logoUrl = logoUrl // Pass the fetched logo URL
                // decimals = fetchedDecimals ?: 8 // Use fetched decimals or default
            )

        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error in getTokenInfo for $contract: ${e.message}", e)
            // Fallback to contract address if any other error occurs
            return@withContext TokenInfo(name = contract, symbol = contract.take(3).uppercase(), contract = contract)
        }
    }

    // XNS_CONTRACT_NAME moved to companion object

    /**
     * Resolves an XNS name to its corresponding Xian address.
     *
     * @param name The XNS name to resolve (e.g., "myname").
     * @return The Xian address (String) if found, or null otherwise.
     */
    suspend fun resolveXnsName(name: String): String? = withContext(Dispatchers.IO) {
        android.util.Log.d("XianNetworkService", "Attempting to resolve XNS name '$name' via simulate_tx")

        try {
            // 1. Construct payload for simulate_tx (matching web wallet)
            val payload = JSONObject().apply {
                put("sender", "") // Sender can be empty for simulate
                put("contract", XNS_CONTRACT_NAME)
                put("function", "get_main_name_to_address")
                put("kwargs", JSONObject().apply {
                    put("name", name)
                })
            }
            android.util.Log.v("XianNetworkService", "XNS Payload: $payload")

            // 2. Convert payload to hex
            val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
            val payloadHex = payloadBytes.joinToString("") { "%02x".format(it) }
            android.util.Log.v("XianNetworkService", "XNS Payload Hex: $payloadHex")


            // 3. Construct URL (ensure quotes are handled correctly)
            // The path itself needs quotes, which then get URL encoded.
            val pathWithQuotes = "\"/simulate_tx/$payloadHex\""
            val encodedPath = java.net.URLEncoder.encode(pathWithQuotes, "UTF-8")
            // URLEncoder might use '+' for spaces, replace with %20 if necessary, though unlikely here.
            // It correctly encodes '"' as %22 and '/' as %2F.
            val simulateUrl = "$rpcUrl/abci_query?path=$encodedPath"

            logURL(simulateUrl, "URL for XNS simulate_tx")

            // 4. Make request
            val request = Request.Builder().url(simulateUrl).build()
            val simulateResponse = client.newCall(request).execute()

            // 5. Parse response
            if (!simulateResponse.isSuccessful) {
                android.util.Log.w("XianNetworkService", "Failed to simulate XNS resolution for '$name': ${simulateResponse.code} ${simulateResponse.message}")
                simulateResponse.close() // Ensure response body is closed
                return@withContext null
            }

            val simulateResponseBody = simulateResponse.body?.string() ?: "{}"
            simulateResponse.close() // Ensure response body is closed
            android.util.Log.d("XianNetworkService", "Simulate XNS response for '$name': $simulateResponseBody")

            val simulateJson = JSONObject(simulateResponseBody)
            // Check for outer 'error' field first
             if (simulateJson.has("error")) {
                 val errorObj = simulateJson.optJSONObject("error")
                 val errorMessage = errorObj?.optString("data", simulateJson.optString("error")) ?: "Unknown error structure"
                 android.util.Log.w("XianNetworkService", "XNS resolution error from node for '$name': $errorMessage")
                 return@withContext null
             }

            val resultObj = simulateJson.optJSONObject("result")
            val responseObj = resultObj?.optJSONObject("response")
            val simulateValue = responseObj?.optString("value") // Base64 encoded value

            if (simulateValue == null || simulateValue.isEmpty() || simulateValue == "AA==") { // AA== is Base64 for empty/null
                android.util.Log.d("XianNetworkService", "XNS simulate response value is null or empty for '$name'.")
                return@withContext null
            }
             android.util.Log.v("XianNetworkService", "XNS simulate Base64 value for '$name': $simulateValue")


            // 6. Decode Base64 result
            val simulateDecodedBytes = Base64.decode(simulateValue, Base64.DEFAULT)
            // The result from simulate_tx might be JSON containing the actual result, e.g., {"result": "ADDRESS"} or just the raw address
            val simulateDecodedRaw = String(simulateDecodedBytes, Charsets.UTF_8)
            android.util.Log.d("XianNetworkService", "XNS simulate decoded raw for '$name': '$simulateDecodedRaw'")

            // Attempt to parse as JSON first, as simulate_tx often wraps results
            var potentialAddress: String? = null
            try {
                 val decodedJson = JSONObject(simulateDecodedRaw)
                 if (decodedJson.has("result")) {
                      potentialAddress = decodedJson.optString("result", null)
                 }
            } catch (e: org.json.JSONException) {
                 // If not JSON, assume the raw decoded string is the address (or "None")
                 potentialAddress = simulateDecodedRaw
            }

            // Clean up potential surrounding quotes (both single and double) and check validity
            val cleanedAddress = potentialAddress?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") // Remove single quotes too

           if (cleanedAddress != null && cleanedAddress != "None" && cleanedAddress.length == 64) { // Basic validation
                 android.util.Log.d("XianNetworkService", "Resolved XNS name '$name' to address: $cleanedAddress")
                 return@withContext cleanedAddress
            } else {
                 android.util.Log.d("XianNetworkService", "Decoded XNS result '$cleanedAddress' is 'None' or invalid for '$name'.")
                 return@withContext null
            }

        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error resolving XNS name '$name' via simulate_tx", e)
            return@withContext null
        }
    }


    // --- Data classes for Contract Methods ---
    data class Argument(val name: String, val type: String)
    // Wrapper class to match the actual JSON structure: {"methods": [...]}
    data class MethodsWrapper(val methods: List<ContractMethod>?)
    data class ContractMethod(val name: String, val arguments: List<Argument>) // Reverted to non-nullable

    /**
     * Fetches the methods and their arguments for a given smart contract.
     *
     * @param contractName The name of the contract to query.
     * @return A list of ContractMethod objects, or null if the contract is not found or an error occurs.
     */
    suspend fun getContractMethods(contractName: String): List<ContractMethod>? = withContext(Dispatchers.IO) {
        if (!checkNodeConnectivity()) {
            android.util.Log.e("XianNetworkService", "No node connection for getContractMethods.")
            return@withContext null
        }

        // Construct the URL exactly like the JavaScript version: RPC + '/abci_query?path="/contract_methods/' + contract + '"'
        // Let OkHttp handle the necessary encoding of the base URL and parameter value, but keep the quotes literal.
        val queryUrl = "$rpcUrl/abci_query?path=\"/contract_methods/$contractName\""
        logURL(queryUrl, "URL for getContractMethods (REST)")

        val request = Request.Builder().url(queryUrl).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("XianNetworkService", "Failed to fetch methods for $contractName: ${response.code} ${response.message}")
                // Assuming 404 or similar might indicate contract not found, but the endpoint might just return empty/null value instead.
                // Let the parsing logic handle potential null values.
                // If the response code indicates a server error (5xx), log it more severely.
                if (response.code >= 500) {
                     android.util.Log.e("XianNetworkService", "Server error (${response.code}) fetching methods for $contractName")
                }
                // Return null for any unsuccessful response for now.
                return@withContext null
            }

            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.v("XianNetworkService", "RAW Contract Methods Response for $contractName: $responseBody") // Verbose log for raw response

            // --- JSON Parsing Logic ---
            // Assuming the response structure is similar to other abci_query calls:
            // { "result": { "response": { "value": "BASE64_ENCODED_JSON_ARRAY" } } }
            val json = JSONObject(responseBody)
            val base64Value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            android.util.Log.v("XianNetworkService", "Extracted Base64 value for $contractName: $base64Value")

            if (base64Value == null || base64Value.isEmpty() || base64Value == "AA==") {
                 android.util.Log.w("XianNetworkService", "No methods found or empty response for contract '$contractName'.")
                 // This could mean the contract exists but has no methods, or doesn't exist.
                 // Returning empty list might be better than null if the contract *might* exist.
                 // Let's return null to match the "not found or error" behaviour described in the function doc.
                 return@withContext null
            }

            // Decode Base64
            android.util.Log.d("XianNetworkService", "Attempting Base64 decode for $contractName...")
            val decodedJsonString: String = try {
                 val decodedBytes = Base64.decode(base64Value, Base64.DEFAULT)
                 String(decodedBytes, Charsets.UTF_8).also {
                     android.util.Log.v("XianNetworkService", "Successfully decoded Base64 for $contractName.")
                 }
            } catch (e: IllegalArgumentException) {
                 android.util.Log.e("XianNetworkService", "BASE64 DECODING FAILED for $contractName. Base64 value was: '$base64Value'", e)
                 return@withContext null
            }

            android.util.Log.i("XianNetworkService", "Attempting to parse GSON for $contractName. Decoded JSON: '$decodedJsonString'")

            // Parse the decoded JSON string using the wrapper class
            try {
                val wrapper: MethodsWrapper = gson.fromJson(decodedJsonString, MethodsWrapper::class.java)

                // Extract the list, handling potential nulls
                val methodsList = wrapper.methods?.mapNotNull { method ->
                    // Ensure name is present and arguments list defaults to empty if null
                    // method.name null check is redundant if parsing directly into ContractMethod with non-nullable name
                    // Keeping the mapping structure for safety in case of unexpected JSON variations
                    if (method.name != null) { // Keep check for robustness during mapping
                         ContractMethod(
                             name = method.name,
                             arguments = method.arguments ?: emptyList()
                         )
                    } else {
                        android.util.Log.w("XianNetworkService", "Skipping method with null name in response for $contractName")
                        null
                    }
                } ?: emptyList() // If wrapper.methods itself is null, return empty list


                android.util.Log.i("XianNetworkService", "GSON PARSING SUCCESS for $contractName. Found ${methodsList.size} methods.")
                return@withContext methodsList // Return the extracted & validated list

            } catch (e: com.google.gson.JsonSyntaxException) {
                 android.util.Log.e("XianNetworkService", "GSON JSON SYNTAX ERROR parsing methods wrapper for $contractName. Decoded JSON was: '$decodedJsonString'", e)
                 return@withContext null // Return null specifically on parsing error
            } catch (e: Exception) {
                 // Catch other potential exceptions during parsing/mapping
                 android.util.Log.e("XianNetworkService", "GSON GENERIC ERROR parsing methods for $contractName. Decoded JSON was: '$decodedJsonString'", e)
                 return@withContext null
            }

        } catch (e: Exception) {
            // Catch exceptions from the network call or initial JSON parsing
            android.util.Log.e("XianNetworkService", "OUTER CATCH: Error fetching or processing contract methods for $contractName: ${e.message}", e)
            return@withContext null // Return null on any other error
        }
    }

    // --- Start of new NFT functions ---

    /**
     * Fetches the keys of NFTs owned by a specific public key.
     * Corresponds to the first GraphQL query in the web version.
     */
    private suspend fun getOwnedNftKeys(publicKey: String): List<String> = withContext(Dispatchers.IO) {
        val graphQLEndpoint = "$rpcUrl/graphql"
        val query = """
            query MyQuery {
              allStates(
                filter: {
                  key: { startsWith: "con_pixel_frames_info.S", endsWith: "owner" }
                  value: { equalTo: "$publicKey" }
                }
                offset: 0
                first: 100
                orderBy: UPDATED_DESC
              ) {
                nodes {
                  key
                }
              }
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("query", query)
        }.toString()

        val request = Request.Builder()
            .url(graphQLEndpoint)
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), requestBody)) // Reverted to deprecated create call to fix build
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("XianNetworkService", "Failed to fetch owned NFT keys: ${response.code} ${response.message}")
                return@withContext emptyList()
            }

            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Owned NFT Keys Response: $responseBody")
            val json = JSONObject(responseBody)
            val nodes = json.optJSONObject("data")?.optJSONObject("allStates")?.optJSONArray("nodes")

            val keys = mutableListOf<String>()
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    val key = node.optString("key", "")
                    // Extract the NFT address part using the logic from the web version
                    // key format: con_pixel_frames_info.S:<nft_address>:owner
                    if (key.contains("con_pixel_frames_info.S") && key.contains(":owner")) {
                         val addressPart = key.substringAfter("con_pixel_frames_info.S") // Gives ":<nft_address>:owner" or "<nft_address>:owner"
                         val nftAddress = addressPart.removePrefix(":").split(":")[0] // Remove leading ":" if present, then split and take first part
                         if (nftAddress.isNotEmpty()) {
                             keys.add(nftAddress)
                             android.util.Log.d("XianNetworkService", "Found NFT Key: $nftAddress")
                        }
                    }
                }
            }
            return@withContext keys
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error fetching owned NFT keys: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Fetches the metadata for a specific NFT key.
     * Corresponds to the second GraphQL query in the web version.
     */
    private suspend fun getNftMetadata(nftKey: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val graphQLEndpoint = "$rpcUrl/graphql"
        // Note: The web version uses specific indices (9 for name, 3 for description).
        // This might be fragile. A more robust approach would be to filter by key suffix if possible.
        val query = """
            query MyQuery {
              i_0: allStates(
                filter: {key: {startsWith: "con_pixel_frames_info.S:$nftKey"}}
              ) {
                nodes {
                  key
                  value
                }
              }
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("query", query)
        }.toString()

        val request = Request.Builder()
            .url(graphQLEndpoint)
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), requestBody)) // Reverted to deprecated create call to fix build
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("XianNetworkService", "Failed to fetch NFT metadata for $nftKey: ${response.code} ${response.message}")
                return@withContext Pair(null, null)
            }

            val responseBody = response.body?.string() ?: "{}"
             android.util.Log.d("XianNetworkService", "NFT Metadata Response for $nftKey: $responseBody")
            val json = JSONObject(responseBody)
            val nodes = json.optJSONObject("data")?.optJSONObject("i_0")?.optJSONArray("nodes")

            var name: String? = null
            var description: String? = null

            if (nodes != null && nodes.length() > 9) { // Check length before accessing indices
                 // Assuming indices 9 and 3 are correct based on web version
                 try {
                     name = nodes.getJSONObject(9)?.optString("value")
                     description = nodes.getJSONObject(3)?.optString("value")
                     android.util.Log.d("XianNetworkService", "Extracted Metadata for $nftKey - Name: $name, Desc: $description")
                 } catch (e: org.json.JSONException) {
                     android.util.Log.e("XianNetworkService", "Error accessing specific indices for NFT metadata $nftKey: ${e.message}")
                     // Fallback: Iterate to find keys ending with specific metadata names if indices fail
                     for (i in 0 until nodes.length()) {
                         val node = nodes.getJSONObject(i)
                         val key = node.optString("key", "")
                         val value = node.optString("value", "")
                         if (key.endsWith(":name")) name = value
                         if (key.endsWith(":description")) description = value
                     }
                     android.util.Log.d("XianNetworkService", "Fallback Metadata for $nftKey - Name: $name, Desc: $description")
                 }
            } else {
                 android.util.Log.w("XianNetworkService", "Metadata nodes array is null or too short for $nftKey")
            }

            return@withContext Pair(name, description)
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error fetching NFT metadata for $nftKey: ${e.message}", e)
            return@withContext Pair(null, null)
        }
    }

    /**
     * Fetches all NFTs owned by the user, including their metadata.
     */
    suspend fun getNfts(publicKey: String): List<NftInfo> = withContext(Dispatchers.IO) {
        android.util.Log.d("XianNetworkService", "Starting NFT fetch for publicKey: $publicKey")
        val nftKeys = getOwnedNftKeys(publicKey)
        if (nftKeys.isEmpty()) {
            android.util.Log.d("XianNetworkService", "No NFT keys found for $publicKey")
            return@withContext emptyList()
        }
        android.util.Log.d("XianNetworkService", "Found ${nftKeys.size} NFT keys for $publicKey")

        val nftInfoList = mutableListOf<NftInfo>()
        for (key in nftKeys) {
             android.util.Log.d("XianNetworkService", "Fetching metadata for NFT key: $key")
            val (name, description) = getNftMetadata(key)
            if (name != null) { // Only add if we could get at least the name
                val nft = NftInfo(
                    contractAddress = key, // Using the key as the unique identifier/address
                    name = name,
                    description = description ?: "No description", // Provide default if null
                    // Construct URLs based on the web version's pattern
                    imageUrl = "https://pixelsnek.xian.org/gif/${key}.gif",
                    viewUrl = "https://pixelsnek.xian.org/frames/${key}"
                )
                nftInfoList.add(nft)
                android.util.Log.d("XianNetworkService", "Added NFT to list: ${nft.name}")
            } else {
                 android.util.Log.w("XianNetworkService", "Skipping NFT with key $key due to missing name in metadata.")
            }
        }
        android.util.Log.d("XianNetworkService", "Finished fetching NFTs. Total found: ${nftInfoList.size}")
        return@withContext nftInfoList
    }

    /**
     * Fetches the reserve balances for the XIAN/USDC pair (pair ID 1)
     * to calculate the current price.
     * @return Pair<Float, Float>? representing (reserve0 (USDC), reserve1 (XIAN)), or null if fetching fails.
     */
    suspend fun getXianPriceInfo(): Pair<Float, Float>? = withContext(Dispatchers.IO) {
        val graphQLEndpoint = "$rpcUrl/graphql" // Use the current RPC URL
        // The query provided by the user
        val query = """
            query PairInfo {
              allStates(filter: {key: {startsWith: "con_pairs.pairs:1:"}}) {
                edges {
                  node {
                    value
                    key
                  }
                }
              }
            }
        """.trimIndent()

        val requestBody = JSONObject().apply {
            put("query", query)
        }.toString()

        val request = Request.Builder()
            .url(graphQLEndpoint)
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), requestBody)) // Reverted to deprecated create call to fix build
            .build()

        try {
            // Check connectivity first
            if (!checkNodeConnectivity()) {
                 android.util.Log.e("XianNetworkService", "No connection to node, cannot fetch price info.")
                 return@withContext null
            }

            android.util.Log.d("XianNetworkService", "Fetching XIAN price info from $graphQLEndpoint")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("XianNetworkService", "Failed to fetch XIAN price info: ${response.code} ${response.message}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "XIAN Price Info Response: $responseBody")
            val json = JSONObject(responseBody)
            val edges = json.optJSONObject("data")?.optJSONObject("allStates")?.optJSONArray("edges")

            var reserve0: Float? = null
            var reserve1: Float? = null

            if (edges != null) {
                for (i in 0 until edges.length()) {
                    val node = edges.getJSONObject(i)?.optJSONObject("node")
                    if (node != null) {
                        val key = node.optString("key")
                        val value = node.optString("value")
                        when (key) {
                            "con_pairs.pairs:1:reserve0" -> {
                                reserve0 = value.toFloatOrNull()
                                android.util.Log.d("XianNetworkService", "Found reserve0: $reserve0")
                            }
                            "con_pairs.pairs:1:reserve1" -> {
                                reserve1 = value.toFloatOrNull()
                                android.util.Log.d("XianNetworkService", "Found reserve1: $reserve1")
                            }
                        }
                    }
                    // Break early if both found
                    if (reserve0 != null && reserve1 != null) break
                }
            }

            if (reserve0 != null && reserve1 != null) {
                return@withContext Pair(reserve0, reserve1)
            } else {
                android.util.Log.e("XianNetworkService", "Could not find reserve0 or reserve1 in response.")
                return@withContext null
            }

        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error fetching XIAN price info: ${e.message}", e)
            return@withContext null
        }
    }


    // --- End of new NFT functions ---

    
    /**
     * Broadcast a signed transaction to the Xian network
     * 
     * @param signedTransaction The signed transaction hex string to broadcast
     * @return A Pair containing: success status (Boolean) and transaction hash (String)
     */
    suspend fun broadcastTransaction(signedTransaction: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node. Aborting transaction broadcast.")
                return@withContext Pair(false, "")
            }
            
            // Log the transaction length and first/last part for debugging
            android.util.Log.d("XianNetworkService", "Broadcasting transaction, length: ${signedTransaction.length}")
            if (signedTransaction.length > 100) {
                android.util.Log.d("XianNetworkService", "Transaction (truncated): ${signedTransaction.substring(0, 50)}...${signedTransaction.substring(signedTransaction.length - 50)}")
            } else {
                android.util.Log.d("XianNetworkService", "Transaction: $signedTransaction")
            }
            
            // KEY: Build URL for broadcast_tx_sync exactly as in the web version
            // Double quotes within the URL are critical but can cause issues
            // with okhttp if not handled correctly
            
            // Creamos la URL base sin las comillas adicionales
            val baseUrl = "$rpcUrl/broadcast_tx_sync?tx="
            
            // Manually add double quotes around the hex transaction
            val fullUrl = baseUrl + "\"" + signedTransaction + "\""
            
            android.util.Log.d("XianNetworkService", "Broadcasting to URL: $fullUrl")
            
            // Make the request using the full URL, allowing OkHttp
            // to do its own URL encoding
            val request = Request.Builder()
                .url(fullUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Broadcast response: $responseBody")
            
            val json = JSONObject(responseBody)
            
            // Improved response analysis
            if (json.has("error")) {
                // There is an explicit error in the response
                val errorJson = json.getJSONObject("error")
                val errorMsg = errorJson.optString("message", "Unknown error")
                android.util.Log.e("XianNetworkService", "Transaction error from node: $errorMsg")
                return@withContext Pair(false, "")
            } else if (json.has("result")) {
                // There is a result - verify the code
                val resultJson = json.getJSONObject("result")
                val hash = resultJson.optString("hash", "")
                val code = resultJson.optInt("code", -1)
                
                // Log detallado del resultado
                android.util.Log.d("XianNetworkService", "Transaction result - hash: $hash, code: $code")
                
                if (code == 0) {
                    // Code 0 indicates total success
                    android.util.Log.d("XianNetworkService", "Transaction successful!")
                    return@withContext Pair(true, hash)
                } else {
                    // Any other code indicates an error
                    val log = resultJson.optString("log", "Unknown error")
                    android.util.Log.e("XianNetworkService", "Transaction failed with code $code: $log")
                    return@withContext Pair(false, hash)
                }
            } else {
                // No error or result - something unexpected happened
                android.util.Log.e("XianNetworkService", "Unexpected response format: $responseBody")
                return@withContext Pair(false, "")
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error broadcasting transaction: ${e.message}", e)
            return@withContext Pair(false, "")
        }
    }
    
    /**
     * Get transaction results by hash
     * 
     * @param txHash The transaction hash to query
     * @return The transaction results as a JSON string
     */
    suspend fun getTransactionResults(txHash: String): String = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node. Aborting transaction result retrieval.")
                return@withContext "{\"error\":\"No connection to node\"}"
            }
            
            // Construir URL para tx
            val url = "$rpcUrl/tx?hash=\"$txHash\""
            
            // Realizar la solicitud
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            
            return@withContext responseBody
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting transaction results: ${e.message}")
            return@withContext "{\"error\":\"${e.message}\"}"
        }
    }
    
    /**
     * Get the chain ID from the Xian network
     * 
     * @return The chain ID as a String
     */
    suspend fun getChainID(): String = withContext(Dispatchers.IO) {
        try {
            // Check if we already have the chain ID cached
            if (chainId != null) {
                return@withContext chainId!!
            }
            
            // Verify connectivity with the node before continuing
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node. Aborting chain ID retrieval.")
                return@withContext "xian"
            }
            
            // Build URL for genesis endpoint as in the web version
            val url = "$rpcUrl/genesis"
            
            // Make the request
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Genesis response: $responseBody")
            
            val json = JSONObject(responseBody)
            
            // Extract chain_id from genesis data
            chainId = json.optJSONObject("result")?.optJSONObject("genesis")?.optString("chain_id", "xian")
            android.util.Log.d("XianNetworkService", "Chain ID: $chainId")
            
            return@withContext chainId ?: "xian"
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting chain ID: ${e.message}")
            return@withContext "xian" // Default fallback value
        }
    }
    
    /**
     * Estima la cantidad de stamps necesarios para una transacción
     * Replica la funcionalidad de estimateStamps en la versión web
     */
    suspend fun estimateStamps(transaction: JSONObject): Int = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node. Aborting stamp estimation.")
                return@withContext 200000 // Higher default value (was 100000)
            }
            
            // Assume 'transaction' is the SIGNED transaction JSON object
            // Serialize the SIGNED transaction to format JSON
            val serializedTransaction = transaction.toString()
            val transactionBytes = serializedTransaction.toByteArray(Charsets.UTF_8)
            val transactionHex = XianCrypto.getInstance().toHexString(transactionBytes)
            
            // Construir URL para calculate_stamps
            val baseUrl = "$rpcUrl/abci_query?path="
            val pathPart = "\"/calculate_stamps/$transactionHex\""
            val encodedPathPart = java.net.URLEncoder.encode(pathPart, "UTF-8")
            val finalPathPart = encodedPathPart.replace("+", "%20")
            val calculateUrl = baseUrl + finalPathPart
            
            android.util.Log.d("XianNetworkService", "Estimando stamps con URL: $calculateUrl")
            
            // Realizar la solicitud
            val response = client.newCall(
                Request.Builder()
                    .url(calculateUrl)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Estimation response: $responseBody")
            
            val json = JSONObject(responseBody)
            val value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (value != null && value != "AA==") {
                // Decodificar el valor Base64
                val decodedBytes = Base64.decode(value, Base64.DEFAULT)
                val decoded = String(decodedBytes)
                
                // Parsear el JSON de respuesta
                val resultJson = JSONObject(decoded)
                val stampsUsed = resultJson.optInt("stamps_used", 0)
                val success = resultJson.optInt("status", -1) == 0
                
                android.util.Log.d("XianNetworkService", "Estimated stamps (no margin): $stampsUsed, success: $success")
                
                // Use the exact estimated value, like in the Prueb version
                // Remove the 30% margin
                android.util.Log.d("XianNetworkService", "Using exact estimated stamps: $stampsUsed")

                // Return the exact estimated value if successful and positive
                return@withContext if (success && stampsUsed > 0) stampsUsed else 200000 // Fallback if status is not 0 or stampsUsed is 0/negative
            } else {
                android.util.Log.e("XianNetworkService", "Could not estimate stamps, using default value")
                return@withContext 200000 // Higher default value (was 100000)
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error estimating stamps: ${e.message}", e)
            return@withContext 200000 // Higher default value (was 100000)
        }
    }

    /**
     * Estimates the stamp cost for a potential transaction without broadcasting it.
     * This constructs the transaction, signs it, and sends it to the estimation endpoint.
     *
     * @param contract The target contract name.
     * @param method The target method name within the contract.
     * @param kwargs The arguments for the method as a JSONObject.
     * @param publicKey The public key of the sender.
     * @param privateKey The private key of the sender (used for signing the estimation tx).
     * @return The estimated stamp cost as a Long, or null if estimation fails or wallet is locked.
     */
    suspend fun estimateTransactionFee(
        contract: String,
        method: String,
        kwargs: JSONObject,
        publicKey: String,
        privateKey: ByteArray // Require private key for signing
    ): Long? = withContext(Dispatchers.IO) {

        try {
            if (!checkNodeConnectivity()) {
                 android.util.Log.e("XianNetworkService", "No node connection for estimateTransactionFee.")
                 return@withContext null
            }

            val chainId = getChainID() // Fetch current chain ID
            val nonce = getNonce(publicKey) // Fetch next nonce

            // 1. Construct the Transaction Payload
            val transactionPayload = JSONObject().apply {
                put("chain_id", chainId)
                put("contract", contract)
                put("function", method)
                put("kwargs", kwargs)
                // Stamps supplied for estimation - use a reasonably high value
                // The actual value doesn't matter much for estimation itself,
                // as the node calculates based on execution, but it needs to be present.
                put("stamps_supplied", 100000)
                put("nonce", nonce)
                put("sender", publicKey) // Sender is derived from public key
            }

            val transaction = JSONObject().apply {
                put("payload", transactionPayload)
                put("metadata", JSONObject().put("signature", "")) // Placeholder for signature
            }
             android.util.Log.d("XianNetworkService", "Estimating Tx: ${transaction.toString(2)}")


            // 2. Sign the Transaction (using the existing helper)
            val signedTx = signTransaction(transaction, privateKey)
             android.util.Log.d("XianNetworkService", "Signed Tx for Estimation: ${signedTx.toString(2)}")


            // 3. Estimate Stamps (using the existing helper)
            // estimateStamps returns Int, convert to Long?
            val estimatedStampsInt = estimateStamps(signedTx)

            if (estimatedStampsInt <= 0) {
                 android.util.Log.w("XianNetworkService", "estimateStamps returned non-positive value: $estimatedStampsInt for tx: ${signedTx.toString(2)}")
                 return@withContext null // Indicate estimation failure
            }

            return@withContext estimatedStampsInt.toLong()

        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error during estimateTransactionFee: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Estimates the stamps required for submitting a new contract.
     *
     * @param contractName The name of the contract to be submitted.
     * @param contractCode The Python code of the contract.
     * @param publicKey The public key of the sender.
     * @param privateKey The private key of the sender (used for signing the estimation transaction).
     * @return The estimated number of stamps required, or 0 if estimation fails.
     */
    suspend fun estimateContractSubmissionFee(
        contractName: String,
        contractCode: String,
        publicKey: String,
        privateKey: ByteArray
    ): Int = withContext(Dispatchers.IO) {
        try {
            val chainId = getChainID()
            val nonce = getNonce(publicKey)

            // Construct payload for submission
            val payload = mapOf(
                "chain_id" to chainId,
                "contract" to "submission",
                "function" to "submit_contract",
                "kwargs" to mapOf(
                    "name" to contractName,
                    "code" to contractCode
                ),
                "stamps_supplied" to 200000 // Use a high value for estimation
            )

            // Sign the transaction for estimation
            val signedTxJson = XianCrypto.signTransaction(payload, privateKey, publicKey, nonce)

            // Call the existing estimateStamps function
            return@withContext estimateStamps(signedTxJson)

        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error estimating contract submission fee: ${e.message}", e)
            return@withContext 0 // Return 0 on error
        }
    }


    /**
     * Send a transaction to the Xian blockchain
     * Método simplificado que maneja todo el proceso de creación, firma y envío de transacciones
     * 
     * @param contract El contrato a llamar
     * @param method El método a invocar
     * @param kwargs Los argumentos para el método
     * @param privateKey La clave privada para firmar
     * @param stampLimit El límite de stamps (gas) para la transacción
     * @return TransactionResult con el resultado de la transacción
     */
    suspend fun sendTransaction(
        contract: String,
        method: String,
        kwargs: JSONObject,
        privateKey: ByteArray,
        stampLimit: Int = 0
    ): TransactionResult = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Verify connection and get public key
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node")
                return@withContext TransactionResult(
                    txHash = "", 
                    success = false, 
                    errors = "No connection to node. Please check your internet connection."
                )
            }
            
            // Get the public key
            val keyPair = XianCrypto.getInstance().createKeyPairFromSeed(privateKey)
            val publicKey = XianCrypto.getInstance().toHexString(keyPair.second)
            android.util.Log.d("XianNetworkService", "Using public key: $publicKey")
            
            // PASO 2: Verificar balance
            val balance = getTokenBalance("currency", publicKey)
            android.util.Log.d("XianNetworkService", "Saldo actual: $balance XIAN")
            
            // PASO 3: Obtener nonce y chain ID
            val nonce = getNonce(publicKey)
            val chainId = getChainID()
            android.util.Log.d("XianNetworkService", "Obtenido - nonce: $nonce, chainId: $chainId")
            
            // STEP 4: Build transaction payload for estimation
            // Use an initial stamp value for estimation
            val initialStampLimit = if (stampLimit > 0) stampLimit else 100000
            
            val estimationPayload = JSONObject().apply {
                put("chain_id", chainId)
                put("sender", publicKey)
                put("nonce", nonce)
                put("contract", contract)
                put("function", method)
                put("kwargs", kwargs)
                put("stamps_supplied", initialStampLimit)
            }
            
            // STEP 5: Sort keys alphabetically for deterministic signature
            val orderedEstimationPayload = sortJsonObject(estimationPayload)
            
            // STEP 6: Create transaction for estimation
            val estimationTx = JSONObject().apply {
                put("payload", orderedEstimationPayload)
                put("metadata", JSONObject().apply {
                    put("signature", "")
                })
            }
            
            // STEP 7: Sign the estimation transaction
            val signedEstimationTx = signTransaction(estimationTx, privateKey)
            
            // PASO 8: Estimar los stamps necesarios
            // PASO 8: Estimar los stamps necesarios using the SIGNED transaction
            // Pass the signedEstimationTx directly to the modified estimateStamps function
            val estimatedStamps = estimateStamps(signedEstimationTx)
            android.util.Log.d("XianNetworkService", "Stamps estimados (con margen): $estimatedStamps")
            
            // Use the estimated stamps directly, applying a sensible minimum fallback
            // Remove complex checks and maximums for now, rely on the Prueb logic's simpler approach
            val finalStampLimit = if (estimatedStamps > 0) {
                estimatedStamps
            } else {
                android.util.Log.w("XianNetworkService", "Estimation failed or returned 0, using default 200000")
                200000 // Default if estimation failed
            }

            android.util.Log.d("XianNetworkService", "Using final stamp limit based on estimation/default: $finalStampLimit")
            
            // Approximate estimation of required XIAN based on stamps
            val stampRate = getStampRate()
            android.util.Log.d("XianNetworkService", "Tasa de stamps obtenida: $stampRate stamps/XIAN")
            
            // Calculate the cost in XIAN with BigDecimal precision
            val stampsBigDecimal = BigDecimal.valueOf(finalStampLimit.toLong())
            val rateBigDecimal = BigDecimal.valueOf(stampRate.toDouble())
            
            // Correct calculation: stamps ÷ (stamps/XIAN) = XIAN
            // Verify that the rate is not zero to avoid division error
            val estimatedXianCostBD = if (rateBigDecimal.compareTo(BigDecimal.ZERO) > 0) {
                stampsBigDecimal.divide(rateBigDecimal, 8, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal("0.1") // Valor por defecto si hay error con la tasa
            }
            
            val estimatedXianCost = estimatedXianCostBD.toFloat()
            
            // Verificar que el costo estimado sea razonable
            val finalEstimatedCost = when {
                estimatedXianCost <= 0.0f -> 0.1f // Minimum value if calculation fails
                estimatedXianCost > 10.0f -> 
                    // If the fee is extraordinarily high, limit it and warn
                    if (contract == "currency" && method == "transfer") {
                        android.util.Log.w("XianNetworkService", "Excessively high fee! Reducing from $estimatedXianCost to 0.5 XIAN")
                        0.5f // For normal transfers, this is more than enough
                    } else {
                        // For other more complex operations, allow up to 5.0 XIAN
                        android.util.Log.w("XianNetworkService", "High fee! Reducing from $estimatedXianCost to 5.0 XIAN")
                        5.0f
                    }
                else -> estimatedXianCost
            }
            
            android.util.Log.d("XianNetworkService", "Costo estimado original: $estimatedXianCost XIAN")
            android.util.Log.d("XianNetworkService", "Costo estimado FINAL: $finalEstimatedCost XIAN (stamps: $finalStampLimit, tasa: $stampRate)")
            
            // Aumentar el margen de tolerancia para evitar rechazos por balance bajo
            if (balance < finalEstimatedCost - 0.1f) {
                android.util.Log.e("XianNetworkService", "Saldo insuficiente: $balance XIAN, se requiere aproximadamente: $finalEstimatedCost XIAN")
                return@withContext TransactionResult(
                    txHash = "", 
                    success = false, 
                    errors = "Insufficient balance ($balance XIAN). You need approximately $finalEstimatedCost XIAN to cover the fees for this transaction."
                )
            }
            
            // STEP 9: Build final transaction payload with estimated stamps
            val txPayload = JSONObject().apply {
                put("chain_id", chainId)
                put("sender", publicKey)
                put("nonce", nonce)
                put("contract", contract)
                put("function", method)
                put("kwargs", kwargs)
                put("stamps_supplied", finalStampLimit)
            }
            
            // STEP 10: Sort keys alphabetically for deterministic signature
            val orderedPayload = sortJsonObject(txPayload)
            android.util.Log.d("XianNetworkService", "Payload ordenado: $orderedPayload")
            
            // PASO 11: Serializar y firmar
            val serializedPayload = orderedPayload.toString()
            val signature = XianCrypto.getInstance().signTransaction(
                serializedPayload, 
                privateKey, 
                publicKey
            )
            android.util.Log.d("XianNetworkService", "Firma generada: $signature")
            
            // STEP 12: Build complete transaction
            val fullTx = JSONObject().apply {
                put("payload", orderedPayload)
                put("metadata", JSONObject().apply {
                    put("signature", signature)
                })
            }
            
            // PASO 13: Serializar a formato hexadecimal
            val txString = fullTx.toString()
            val txBytes = txString.toByteArray(Charsets.UTF_8)
            val txHex = XianCrypto.getInstance().toHexString(txBytes)
            android.util.Log.d("XianNetworkService", "Hex transaction (first 50 characters): ${txHex.take(50)}...")
            
            // PASO 14: Broadcast a la red
            val baseUrl = "$rpcUrl/broadcast_tx_sync?tx="
            val urlWithTx = baseUrl + "\"" + txHex + "\""
            android.util.Log.d("XianNetworkService", "URL de broadcast: $urlWithTx")
            
            val request = Request.Builder()
                .url(urlWithTx)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Respuesta de broadcast: $responseBody")
            
            // PASO 15: Analizar respuesta
            val responseJson = JSONObject(responseBody)
            
            // Check if there is an explicit error
            if (responseJson.has("error")) {
                val errorMsg = responseJson.getJSONObject("error").optString("message", "Unknown error")
                android.util.Log.e("XianNetworkService", "Error en respuesta: $errorMsg")
                return@withContext TransactionResult(
                    txHash = "", 
                    success = false, 
                    errors = errorMsg
                )
            }
            
            // Verificar si hay un resultado
            if (responseJson.has("result")) {
                val resultJson = responseJson.getJSONObject("result")
                val hash = resultJson.optString("hash", "")
                val code = resultJson.optInt("code", -1)
                
                android.util.Log.d("XianNetworkService", "Response code: $code, Hash: $hash")
                
                // Code 0 is success
                if (code == 0) {
                    return@withContext TransactionResult(
                        txHash = hash,
                        success = true
                    )
                } else {
                    // Any other code is an error
                    val log = resultJson.optString("log", "Unknown error")
                    android.util.Log.e("XianNetworkService", "Transaction error with code $code: $log")
                    
                    // Improve the error message to make it clearer
                    val userFriendlyError = if (log.contains("too few stamps")) {
                        "Transaction error: Insufficient stamps. Please try again with a smaller amount or contact support."
                    } else {
                        "Transaction error: $log"
                    }
                    
                    return@withContext TransactionResult(
                        txHash = hash,
                        success = false,
                        errors = userFriendlyError
                    )
                }
            }
            
            // If there's no error or result, something strange happened
            android.util.Log.e("XianNetworkService", "Unexpected response: $responseBody")
            return@withContext TransactionResult(
                txHash = "",
                success = false,
                errors = "Unexpected response from node"
            )
            
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Exception in sendTransaction: ${e.message}", e)
            return@withContext TransactionResult(
                txHash = "",
                success = false,
                errors = "Exception: ${e.message}"
            )
        }
    }
    
    /**
     * Obtiene la tasa de conversión de stamps a XIAN
     * @return La tasa de stamps por XIAN
     */
    suspend fun getStampRate(): Float = withContext(Dispatchers.IO) {
        try {
            // Verificar conectividad con el nodo antes de continuar
            val isConnected = checkNodeConnectivity()
            if (!isConnected) {
                android.util.Log.e("XianNetworkService", "No connection to any available node. Using default rate.")
                return@withContext 10000.0f // More reasonable default value (was 20000.0f)
            }
            
            // Construir URL para obtener la tasa de stamps
            val url = "$rpcUrl/abci_query?path=\"/get/stamp_cost.S:value\""
            
            // Realizar la solicitud
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()
            
            val responseBody = response.body?.string() ?: "{}"
            android.util.Log.d("XianNetworkService", "Respuesta de tasa de stamps: $responseBody")
            val json = JSONObject(responseBody)
            val value = json.optJSONObject("result")?.optJSONObject("response")?.optString("value")
            
            if (value != null && value != "AA==") {
                // Decodificar el valor Base64
                val decodedBytes = Base64.decode(value, Base64.DEFAULT)
                val decoded = String(decodedBytes)
                android.util.Log.d("XianNetworkService", "Valor de tasa de stamps decodificado: $decoded")
                
                try {
                    // Try converting to double precision to avoid problems with scientific notation
                    val stampRateValue = decoded.toDoubleOrNull()
                    
                    if (stampRateValue != null) {
                        // The rate is stamps per XIAN, so it should be a value like 100,000
                        // If it's abnormally low (like 0.0001), it might be inverted
                        val stampRate = if (stampRateValue < 1.0) {
                            // If it's extremely low, assume it's inverted (XIAN per stamp)
                            // and correct it (convert to stamps per XIAN)
                            android.util.Log.d("XianNetworkService", "Corrigiendo tasa invertida: $stampRateValue")
                            (1.0 / stampRateValue).toFloat()
                        } else {
                            // Seems reasonable, use as is
                            stampRateValue.toFloat()
                        }
                        
                        // Verify the rate is within a reasonable range to avoid excessive costs
                        val finalRate = when {
                            stampRate < 1000.0f -> 10000.0f // Demasiado bajo, usar valor por defecto
                            stampRate > 1000000.0f -> 100000.0f // Demasiado alto, limitar
                            else -> stampRate
                        }
                        
                        android.util.Log.d("XianNetworkService", "Tasa de stamps FINAL: $finalRate stamps/XIAN")
                        return@withContext finalRate
                    } else {
                        android.util.Log.e("XianNetworkService", "Could not convert to number: $decoded")
                        return@withContext 10000.0f // Use default value
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XianNetworkService", "Error procesando tasa de stamps: ${e.message}")
                    return@withContext 10000.0f // Valor por defecto en caso de error
                }
            } else {
                android.util.Log.e("XianNetworkService", "Could not get stamp rate, using default value")
                return@withContext 10000.0f // More reasonable (was 20000.0f)
            }
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error getting stamp rate: ${e.message}", e)
            return@withContext 10000.0f // More reasonable (was 20000.0f)
        }
    }
    
    /**
     * Función auxiliar para ordenar un JSONObject por sus claves
     */
    private fun sortJsonObject(jsonObj: JSONObject): JSONObject {
        val sorted = JSONObject()
        val keys = ArrayList<String>()
        
        val iterator = jsonObj.keys()
        while (iterator.hasNext()) {
            keys.add(iterator.next())
        }
        
        keys.sort()
        
        for (key in keys) {
            sorted.put(key, jsonObj.get(key))
        }
        
        return sorted
    }
    
    /**
     * Firma una transacción exactamente como se hace en la versión web
     * 
     * @param transaction El objeto de transacción sin firmar
     * @param privateKey La clave privada para firmar la transacción
     * @return El objeto de transacción firmado
     */
    suspend fun signTransaction(transaction: JSONObject, privateKey: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        try {
            // Get the public key from the wallet manager
            val publicKey = walletManager.getPublicKey() ?: throw Exception("Could not get public key")
            
            // Obtener el nonce
            val nonce = getNonce(publicKey)
            android.util.Log.d("XianNetworkService", "Nonce for the transaction: $nonce")
            
            // Clonar el payload para no modificar el original
            val payload = JSONObject(transaction.getJSONObject("payload").toString())
            
            // Agregar el nonce y el sender al payload
            payload.put("nonce", nonce)
            payload.put("sender", publicKey)
            
            android.util.Log.d("XianNetworkService", "Payload con nonce y sender: $payload")
            
            // Sort the keys in the payload (to generate a deterministic signature)
            // KEY: Use a more careful approach to sort the keys
            val orderedPayload = JSONObject()
            
            // Get all keys and sort them alphabetically
            val keys = ArrayList<String>()
            val iterator = payload.keys()
            while (iterator.hasNext()) {
                keys.add(iterator.next())
            }
            keys.sort()
            
            // Construir el payload ordenado
            for (key in keys) {
                orderedPayload.put(key, payload.get(key))
            }
            
            android.util.Log.d("XianNetworkService", "Payload ordenado: $orderedPayload")
            
            // Convertir el payload ordenado a cadena
            val serializedTransaction = orderedPayload.toString()
            android.util.Log.d("XianNetworkService", "Payload serializado: $serializedTransaction")
            
            // Convert to bytes using UTF-8, same as in the web version
            val transactionBytes = serializedTransaction.toByteArray(Charsets.UTF_8)
            
            // Combine the private key and public key as in the web version
            val combinedKey = ByteArray(64)
            System.arraycopy(privateKey, 0, combinedKey, 0, 32)
            System.arraycopy(XianCrypto.getInstance().fromHexString(publicKey), 0, combinedKey, 32, 32)
            
            // Usar Ed25519Signer con la clave combinada
            val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
            val privateKeyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(combinedKey, 0)
            signer.init(true, privateKeyParams)
            signer.update(transactionBytes, 0, transactionBytes.size)
            val signature = signer.generateSignature()
            
            // Convertir firma a formato hexadecimal
            val signatureHex = XianCrypto.getInstance().toHexString(signature)
            android.util.Log.d("XianNetworkService", "Firma generada: $signatureHex")
            
            // Create a new transaction object with the sorted payload
            val signedTransaction = JSONObject()
            signedTransaction.put("payload", orderedPayload)
            signedTransaction.put("metadata", JSONObject().apply {
                put("signature", signatureHex)
            })
            
            android.util.Log.d("XianNetworkService", "Final signed transaction: $signedTransaction")
            
            return@withContext signedTransaction
        } catch (e: Exception) {
            android.util.Log.e("XianNetworkService", "Error signing transaction: ${e.message}", e)
            throw e
        }
    }
}